package com.aitrip.service;

import com.aitrip.entity.AiGenerationLog;
import com.aitrip.mapper.AiGenerationLogMapper;
import com.aitrip.mapper.ItineraryMapper;
import com.aitrip.mapper.RouteNodeMapper;
import com.aitrip.mapper.TransportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 异步行程生成服务。
 * 将耗时的 AI + 高德调用放入后台线程，前端通过 Redis 轮询进度。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncGenerationService {

    /** Redis 进度 key 前缀 */
    private static final String PROGRESS_KEY_PREFIX = "trip:generate:progress:";

    private final ItineraryBizService itineraryBizService;
    private final AiGenerationLogMapper logMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 异步生成完整行程。
     *
     * @param logId    t_ai_generation_log 主键
     * @param userId   用户ID
     * @param rawText  用户原文
     * @param cityHint 城市提示
     */
    @Async
    public void generateFullItineraryAsync(Long logId, Long userId, String rawText,
                                            String cityHint, String startDate,
                                            String endDate,
                                            String startTime, String endTime,
                                            List<String> interestTags,
                                            Boolean isGenerateHotel) {
        String progressKey = PROGRESS_KEY_PREFIX + logId;
        try {
            updateProgress(logId, progressKey, 5, "AI 正在理解你的需求...");

            Long itineraryId = itineraryBizService.generateFullItinerary(
                    userId, rawText, cityHint, startDate, endDate,
                    startTime, endTime,
                    interestTags,
                    isGenerateHotel,
                    (pct, msg) -> updateProgress(logId, progressKey, pct, msg));

            updateProgress(logId, progressKey, 100, "生成完成");

            // 更新日志状态
            AiGenerationLog logRecord = logMapper.selectById(logId);
            if (logRecord != null) {
                logRecord.setStatus("SUCCESS");
                logRecord.setItineraryId(itineraryId);
                logRecord.setProgress(100);
                logMapper.updateById(logRecord);
            }

            // 进度缓存保留 5 分钟后自动过期
            stringRedisTemplate.expire(progressKey, 5, TimeUnit.MINUTES);

        } catch (Exception e) {
            log.error("[AsyncGen] 异步生成失败 logId={}: {}", logId, e.getMessage());
            updateProgress(logId, progressKey, -1, "生成失败: " + e.getMessage());

            AiGenerationLog logRecord = logMapper.selectById(logId);
            if (logRecord != null) {
                logRecord.setStatus("FAILED");
                logRecord.setErrorMsg(e.getMessage());
                logMapper.updateById(logRecord);
            }
        }
    }

    /**
     * 查询生成进度。
     *
     * @param logId 生成日志 ID
     * @return ProgressInfo，若 key 不存在返回 null
     */
    public ProgressInfo getProgress(Long logId) {
        String key = PROGRESS_KEY_PREFIX + logId;
        String val = stringRedisTemplate.opsForValue().get(key);
        if (val == null) return null;

        String[] parts = val.split("\\|", 2);
        int progress = Integer.parseInt(parts[0]);
        String msg = parts.length > 1 ? parts[1] : "";

        Long itineraryId = null;
        if (progress >= 100) {
            AiGenerationLog logRecord = logMapper.selectById(logId);
            if (logRecord != null) {
                itineraryId = logRecord.getItineraryId();
            }
        }
        return new ProgressInfo(progress, msg, itineraryId);
    }

    private void updateProgress(Long logId, String progressKey, int progress, String msg) {
        String val = progress + "|" + msg;
        stringRedisTemplate.opsForValue().set(progressKey, val, 30, TimeUnit.MINUTES);

        AiGenerationLog logRecord = logMapper.selectById(logId);
        if (logRecord != null) {
            logRecord.setProgress(progress);
            logRecord.setProgressMsg(msg);
            if (progress == -1) {
                logRecord.setStatus("FAILED");
            } else if (progress >= 100) {
                logRecord.setStatus("SUCCESS");
            } else if (progress > 0) {
                logRecord.setStatus("PROCESSING");
            }
            logMapper.updateById(logRecord);
        }
    }

    public record ProgressInfo(int progress, String message, Long itineraryId) {
        public boolean isFinished() { return progress >= 100 || progress < 0; }
        public boolean isFailed() { return progress < 0; }
    }
}
