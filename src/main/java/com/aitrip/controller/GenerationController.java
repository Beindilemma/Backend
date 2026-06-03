package com.aitrip.controller;

import com.aitrip.entity.User;
import com.aitrip.mapper.UserMapper;
import com.aitrip.result.Result;
import com.aitrip.service.UserQuotaService;
import com.aitrip.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 生成相关辅助接口：配额查询等。
 */
@RestController
@RequestMapping("/generation")
@RequiredArgsConstructor
public class GenerationController {

    private final UserQuotaService quotaService;
    private final UserMapper userMapper;

    /**
     * 查询当前用户的生成配额与累计生成次数。
     */
    @GetMapping("/quota")
    public Result<Map<String, Object>> quota() {
        Long userId = UserContext.getUserId();
        UserQuotaService.QuotaInfo info = quotaService.getQuotaInfo(userId);

        int totalTripGenerate = 0;
        if (userId != null) {
            User user = userMapper.selectById(userId);
            totalTripGenerate = user != null && user.getTotalTripGenerate() != null
                    ? user.getTotalTripGenerate() : 0;
        }

        return Result.success(Map.of(
                "limit", info.limit(),
                "used", info.used(),
                "remaining", info.remaining(),
                "unlimited", info.unlimited(),
                "totalTripGenerate", totalTripGenerate));
    }
}
