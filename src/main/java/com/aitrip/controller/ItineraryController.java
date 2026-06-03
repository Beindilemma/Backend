package com.aitrip.controller;

import com.aitrip.dto.itinerary.ItineraryGenerateRequest;
import com.aitrip.dto.itinerary.ReorderNodesRequest;
import com.aitrip.exception.BusinessException;
import com.aitrip.result.Result;
import com.aitrip.result.ResultCode;
import com.aitrip.entity.AiGenerationLog;
import com.aitrip.mapper.AiGenerationLogMapper;
import com.aitrip.service.AsyncGenerationService;
import com.aitrip.service.ItineraryBizService;
import com.aitrip.service.ItineraryNodeReplaceService;
import com.aitrip.service.ItineraryService;
import com.aitrip.service.UserQuotaService;
import com.aitrip.utils.UserContext;
import com.aitrip.vo.itinerary.ItineraryGenerateResponse;
import com.aitrip.vo.itinerary.ItineraryListVO;
import com.aitrip.vo.itinerary.ItineraryVO;
import com.aitrip.vo.itinerary.ReorderNodesResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 行程相关接口：生成、详情、同日重排、历史列表。
 */
@RestController
@RequestMapping("/itinerary")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryBizService itineraryBizService;
    private final ItineraryService itineraryService;
    private final ItineraryNodeReplaceService itineraryNodeReplaceService;
    private final UserQuotaService userQuotaService;
    private final AsyncGenerationService asyncGenerationService;
    private final AiGenerationLogMapper aiGenerationLogMapper;

    /**
     * 生成完整行程（含三餐、小吃、景点、酒店、通勤推荐），可能耗时 30～120 秒。
     */
    @PostMapping("/generate")
    public Result<ItineraryGenerateResponse> generate(
            @Valid @RequestBody ItineraryGenerateRequest request,
            HttpServletResponse response) {
        Long tokenUserId = UserContext.getUserId();
        if (tokenUserId == null || !tokenUserId.equals(request.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        response.setHeader("X-Processing-Hint", "Sync generation may take 30-120s; keep connection open.");

        // 检查用户生成配额
        userQuotaService.checkAndConsume(tokenUserId);

        Long itineraryId = itineraryBizService.generateFullItinerary(
                request.getUserId(),
                request.getText(),
                request.getCity(),
                request.getStartDate(),
                request.getEndDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getInterestTags(),
                request.getIsGenerateHotel());

        return Result.success(new ItineraryGenerateResponse(
                itineraryId, "行程生成成功，可使用 itineraryId 查询详情。"));
    }

    /**
     * 【异步】生成完整行程，返回生成日志 ID，前端轮询进度。
     */
    @PostMapping("/generate/async")
    public Result<Map<String, Object>> generateAsync(
            @Valid @RequestBody ItineraryGenerateRequest request) {
        Long tokenUserId = UserContext.getUserId();
        if (tokenUserId == null || !tokenUserId.equals(request.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        userQuotaService.checkAndConsume(tokenUserId);

        AiGenerationLog logRecord = new AiGenerationLog();
        logRecord.setUserId(tokenUserId);
        logRecord.setStatus("PENDING");
        logRecord.setProgress(0);
        logRecord.setProgressMsg("排队中...");
        logRecord.setRequestText(request.getText());
        logRecord.setCity(request.getCity());
        aiGenerationLogMapper.insert(logRecord);

        asyncGenerationService.generateFullItineraryAsync(
                logRecord.getId(), tokenUserId, request.getText(),
                request.getCity(), request.getStartDate(),
                request.getEndDate(),
                request.getStartTime(),
                request.getEndTime(),
                request.getInterestTags(),
                request.getIsGenerateHotel());

        return Result.success(Map.of(
                "logId", logRecord.getId(),
                "message", "已提交异步生成任务，可通过 logId 轮询进度"));
    }

    /**
     * 查询异步生成进度。
     */
    @GetMapping("/generate/progress/{logId}")
    public Result<Map<String, Object>> generationProgress(@PathVariable Long logId) {
        AsyncGenerationService.ProgressInfo progress = asyncGenerationService.getProgress(logId);
        if (progress == null) {
            return Result.success(Map.of("exists", false));
        }
        return Result.success(Map.of(
                "exists", true,
                "progress", progress.progress(),
                "message", progress.message(),
                "finished", progress.isFinished(),
                "failed", progress.isFailed(),
                "itineraryId", progress.itineraryId() != null ? progress.itineraryId() : 0));
    }

    /**
     * 行程详情（按天分组的嵌套结构）。
     */
    @GetMapping("/{id}/detail")
    public Result<ItineraryVO> detail(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        ItineraryVO vo = itineraryService.getDetailGrouped(id, userId);
        return Result.success(vo);
    }

    /**
     * 获取用户的历史行程列表。
     */
    @GetMapping("/list")
    public Result<List<ItineraryListVO>> listHistory() {
        Long userId = UserContext.getUserId();
        List<ItineraryListVO> list = itineraryService.getUserHistoryList(userId);
        return Result.success(list);
    }

    /**
     * 删除整个行程（仅所有者）。级联删除节点、交通、社区数据。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteItinerary(@PathVariable("id") Long id) {
        Long userId = requireUserId();
        itineraryService.deleteItinerary(userId, id);
        return Result.success();
    }

    /**
     * 同一天内拖拽调整顺序。
     */
    @PostMapping("/{itineraryId}/nodes/reorder")
    public Result<ReorderNodesResponse> reorderNodes(
            @PathVariable("itineraryId") Long itineraryId,
            @Valid @RequestBody ReorderNodesRequest body) {
        Long userId = requireUserId();
        ReorderNodesResponse resp = itineraryNodeReplaceService.reorderNodes(userId, itineraryId, body);
        return Result.success(resp);
    }

    private static Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        return userId;
    }
}
