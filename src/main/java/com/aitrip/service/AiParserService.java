package com.aitrip.service;

import com.aitrip.config.AiLlmProperties;
import com.aitrip.dto.ai.AiFullDayPlan;
import com.aitrip.dto.ai.AiFullItineraryResponse;
import com.aitrip.exception.BusinessException;
import com.aitrip.prompt.ItineraryAiPrompts;
import com.aitrip.result.ResultCode;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 行程解析服务。
 * 调用大模型（DeepSeek / OpenAI 兼容接口），将用户的自然语言描述解析为完整的每日行程计划，
 * 包含三餐、小吃、景点、酒店，以及推荐理由和交通建议。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiParserService {

    private static final Pattern JSON_OBJECT_IN_MARKDOWN = Pattern.compile(
            "```(?:json)?\\s*([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final AiLlmProperties llmProperties;

    /**
     * 将自然语言旅游描述解析为完整每日行程（含三餐、小吃、景点、酒店、交通）。
     *
     * @param cityAnchor 请求中的城市（与生成接口 city 一致）；可为空，由模型仅从用户原文推断目的地
     * @param startDate    可选，ISO 日期 yyyy-MM-dd，已拼入用户消息以约束第 1 天
     * @param endDate      可选，ISO 日期 yyyy-MM-dd
     */
    public AiFullItineraryResponse parseFullItinerary(String travelText, String cityAnchor,
                                                      String startDate, String endDate,
                                                      String startTime, String endTime,
                                                      List<String> interestTags) {
        if (!StringUtils.hasText(travelText)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), "旅游描述不能为空");
        }
        String userContent = ItineraryAiPrompts.buildTravelUserContent(travelText, startDate, endDate);
        String city = cityAnchor != null ? cityAnchor.trim() : "";
        String systemPrompt = ItineraryAiPrompts.buildFullItinerarySystemPrompt(city, startTime, endTime, interestTags);
        Exception lastError = null;

        for (int attempt = 1; attempt <= llmProperties.getMaxRetries(); attempt++) {
            try {
                String rawOutput = callChatCompletions(systemPrompt, userContent, attempt);
                logRawIo(systemPrompt, userContent, attempt, rawOutput);

                String jsonSlice = extractJsonObjectString(rawOutput);
                JSONObject root = JSON.parseObject(jsonSlice);
                if (root == null) {
                    throw new IllegalArgumentException("JSON 根节点为空");
                }
                if (root.containsKey("error")) {
                    handleModelError(root.getString("error"));
                }

                AiFullItineraryResponse resp = JSON.parseObject(jsonSlice, AiFullItineraryResponse.class);
                validateFullResponse(resp);
                return resp;
            } catch (BusinessException e) {
                throw e;
            } catch (JSONException | IllegalArgumentException e) {
                lastError = e;
                log.warn("[AiParser] attempt {}/{} full parse failed: {}", attempt, llmProperties.getMaxRetries(), e.getMessage());
            } catch (RestClientException e) {
                lastError = e;
                log.warn("[AiParser] attempt {}/{} HTTP failed: {}", attempt, llmProperties.getMaxRetries(), e.getMessage());
            } catch (Exception e) {
                lastError = e;
                log.warn("[AiParser] attempt {}/{} unexpected: {}", attempt, llmProperties.getMaxRetries(), e.getMessage());
            }
        }

        if (llmProperties.isReturnEmptyOnFailure()) {
            log.error("[AiParser] all attempts exhausted. lastError={}",
                    lastError != null ? lastError.getMessage() : "unknown");
            return null;
        }
        String detail = lastError != null ? lastError.getMessage() : "未知错误";
        throw new BusinessException(ResultCode.AI_PARSE_FAILED.getCode(),
                ResultCode.AI_PARSE_FAILED.getMessage() + "：" + detail);
    }

    private void validateFullResponse(AiFullItineraryResponse resp) {
        if (resp == null || resp.getDailyPlans() == null || resp.getDailyPlans().isEmpty()) {
            throw new IllegalArgumentException("daily_plans 为空或缺失");
        }
        int totalDays = resp.getTotalDays() != null ? resp.getTotalDays() : 1;
        if (totalDays < 1) totalDays = 1;
        if (totalDays > 5) totalDays = 5;

        if (resp.getDailyPlans().size() != totalDays) {
            throw new IllegalArgumentException("daily_plans 数量与 total_days 不一致");
        }

        // 校验每天的节点
        for (int d = 0; d < resp.getDailyPlans().size(); d++) {
            AiFullDayPlan day = resp.getDailyPlans().get(d);
            if (day.getDay() == null || day.getDay() < 1 || day.getDay() > totalDays) {
                throw new IllegalArgumentException("第" + (d + 1) + "天 day 无效");
            }
            List<AiFullDayPlan.AiFullNode> nodes = day.getNodes();
            if (nodes == null || nodes.isEmpty()) {
                throw new IllegalArgumentException("第" + day.getDay() + "天缺少节点");
            }

            // 检查必须包含的节点类型
            boolean hasBreakfast = false, hasLunch = false, hasDinner = false, hasHotel = false;
            int spotCount = 0, snackCount = 0;

            for (AiFullDayPlan.AiFullNode n : nodes) {
                if (!StringUtils.hasText(n.getName())) {
                    throw new IllegalArgumentException("第" + day.getDay() + "天存在名称为空的节点");
                }
                String type = n.getType() != null ? n.getType().toUpperCase() : "";
                switch (type) {
                    case "BREAKFAST" -> hasBreakfast = true;
                    case "LUNCH" -> hasLunch = true;
                    case "DINNER" -> hasDinner = true;
                    case "HOTEL" -> hasHotel = true;
                    case "SPOT" -> spotCount++;
                    case "SNACK" -> snackCount++;
                }
                if (n.getStayTime() == null || n.getStayTime() < 0) {
                    n.setStayTime(60);
                }
                if (n.getStayTime() > 480 && !"HOTEL".equals(type)) {
                    n.setStayTime(180);
                }
            }

            if (!hasBreakfast) log.warn("[AiParser] 第{}天缺少早餐", day.getDay());
            if (!hasLunch) log.warn("[AiParser] 第{}天缺少午餐", day.getDay());
            if (!hasDinner) log.warn("[AiParser] 第{}天缺少晚餐", day.getDay());
            if (!hasHotel) log.warn("[AiParser] 第{}天缺少酒店", day.getDay());
            if (spotCount < 2) log.warn("[AiParser] 第{}天景点不足2个（{}个）", day.getDay(), spotCount);
            if (snackCount < 4) log.warn("[AiParser] 第{}天小吃不足4个（{}个）", day.getDay(), snackCount);
        }
    }

    private static void handleModelError(String errorCode) {
        if (!StringUtils.hasText(errorCode)) return;
        String e = errorCode.trim();
        if ("INVALID_INPUT".equals(e)) {
            throw new BusinessException(ResultCode.AI_ITINERARY_INVALID_INPUT);
        }
        if ("INSUFFICIENT_INFO".equals(e)) {
            throw new BusinessException(ResultCode.AI_ITINERARY_INSUFFICIENT_INFO);
        }
        throw new BusinessException(ResultCode.AI_PARSE_FAILED.getCode(),
                "AI 返回未知错误：" + e);
    }

    private String callChatCompletions(String systemPrompt, String userContent, int attempt) {
        if (!StringUtils.hasText(llmProperties.getApiKey())) {
            throw new BusinessException(ResultCode.FAILED.getCode(), "未配置 ai.llm.api-key");
        }
        if (!StringUtils.hasText(llmProperties.getChatUrl())) {
            throw new BusinessException(ResultCode.FAILED.getCode(), "未配置 ai.llm.chat-url");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", llmProperties.getModel());
        body.put("temperature", llmProperties.getTemperature());
        // 增加 max_tokens 以支持完整行程的大输出
        body.put("max_tokens", 8192);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userContent));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.getApiKey().trim());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        log.debug("[AiParser] POST {} model={} attempt={}", llmProperties.getChatUrl(), llmProperties.getModel(), attempt);

        ResponseEntity<String> response = restTemplate.exchange(
                llmProperties.getChatUrl(),
                HttpMethod.POST,
                entity,
                String.class);

        if (!response.getStatusCode().is2xxSuccessful() || !StringUtils.hasText(response.getBody())) {
            throw new IllegalStateException("LLM HTTP " + response.getStatusCode());
        }

        JSONObject root = JSON.parseObject(response.getBody());
        JSONArray choices = root.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("LLM choices 为空");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message.getString("content");
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("LLM content 为空");
        }
        return content.trim();
    }

    /**
     * 从模型原文中提取 JSON 对象：优先 Markdown 代码块，再按括号配对截取首个完整对象。
     */
    static String extractJsonObjectString(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalArgumentException("模型输出为空");
        }
        String s = raw.trim();
        Matcher m = JSON_OBJECT_IN_MARKDOWN.matcher(s);
        if (m.find()) {
            s = m.group(1).trim();
        }
        int start = s.indexOf('{');
        if (start < 0) {
            throw new IllegalArgumentException("未找到 JSON 对象");
        }
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inString) {
                if (escape) { escape = false; }
                else if (c == '\\') { escape = true; }
                else if (c == '"') { inString = false; }
            } else {
                if (c == '"') { inString = true; }
                else if (c == '{') { depth++; }
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return s.substring(start, i + 1).trim();
                    }
                }
            }
        }
        throw new IllegalArgumentException("未找到完整 JSON 对象");
    }

    private void logRawIo(String systemPrompt, String userContent, int attempt, String rawOutput) {
        log.info("[AiParser] ========== AI 调用 attempt={}/{} ==========", attempt, llmProperties.getMaxRetries());
        log.info("[AiParser] --- System Prompt ({} chars) ---\n{}", systemPrompt.length(), systemPrompt);
        log.info("[AiParser] --- User Content ({} chars) ---\n{}", userContent.length(), userContent);
        log.info("[AiParser] --- Raw Output ({} chars) ---\n{}",
                rawOutput != null ? rawOutput.length() : 0, rawOutput);
        log.info("[AiParser] ========== AI 调用结束 ==========");
    }
}
