package com.aitrip;

import com.aitrip.config.AiLlmProperties;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 {@code application-local.yml}（或环境）中的高德、DeepSeek Key 是否已正确加载且可被厂商接口接受。
 * <p>需要本机可访问外网；并需能正常启动 Spring 上下文（MySQL、Redis 等按主配置可用）。</p>
 * <ul>
 *   <li>高德：使用 {@code v3/ip} 校验 Web 服务 Key（比地理编码更不易触发 30001 等业务错误）。</li>
 *   <li>DeepSeek：若返回 402 余额不足，则跳过用例（Key 一般仍有效，需充值后再做完整校验）。</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ApiKeySmokeTest {

    private static final String AMAP_PLACEHOLDER = "your_amap_web_key_here";
    private static final String LLM_PLACEHOLDER = "your_llm_api_key_here";

    @Autowired
    private AiLlmProperties aiLlmProperties;

    @Autowired
    private RestTemplate restTemplate;

    @Value("${amap.key}")
    private String amapKey;

    @Test
    void amapKey_isConfigured_andWebServiceAcceptsKey() {
        Assumptions.assumeFalse(isPlaceholderAmap(),
                "跳过：amap.key 未配置或为占位符（请配置 application-local.yml）");

        String uri = UriComponentsBuilder.fromUriString("https://restapi.amap.com/v3/ip")
                .queryParam("key", amapKey.trim())
                .toUriString();

        ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotBlank();

        JSONObject root = JSON.parseObject(response.getBody());
        assertThat(root.getString("status"))
                .as("高德 status=1 表示 Key 可用；若失败请到控制台检查 Key 类型(Web服务)、配额与安全设置。body=%s", response.getBody())
                .isEqualTo("1");
    }

    @Test
    void deepSeekKey_isConfigured_andChatCompletionsSucceeds() {
        Assumptions.assumeFalse(isPlaceholderLlm(),
                "跳过：ai.llm.api-key 未配置或为占位符（请配置 application-local.yml）");

        String key = aiLlmProperties.getApiKey().trim();
        assertThat(key).startsWith("sk-");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", aiLlmProperties.getModel());
        body.put("max_tokens", 8);
        body.put("temperature", 0);
        body.put("messages", List.of(
                Map.of("role", "user", "content", "只回复一个字：好")
        ));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(key);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    aiLlmProperties.getChatUrl(),
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class);

            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).isNotBlank();

            JSONObject root = JSON.parseObject(response.getBody());
            assertThat(root.containsKey("choices")).isTrue();
            String content = root.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
            assertThat(content).isNotBlank();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == 402) {
                Assumptions.assumeTrue(false,
                        "DeepSeek 返回 402（Insufficient Balance），跳过聊天校验；API Key 通常仍有效，请充值后再运行本用例。");
            }
            throw e;
        } catch (ResourceAccessException e) {
            Assumptions.assumeTrue(false,
                    "DeepSeek 请求读超时或网络异常，跳过聊天校验（RestTemplate 读超时已设为 120s）。原因：" + e.getMessage());
        }
    }

    private boolean isPlaceholderAmap() {
        return !StringUtils.hasText(amapKey) || AMAP_PLACEHOLDER.equals(amapKey.trim());
    }

    private boolean isPlaceholderLlm() {
        String k = aiLlmProperties.getApiKey();
        return !StringUtils.hasText(k) || LLM_PLACEHOLDER.equals(k.trim());
    }
}
