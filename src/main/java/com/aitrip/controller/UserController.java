package com.aitrip.controller;

import com.aitrip.dto.WechatLoginRequest;
import com.aitrip.result.Result;
import com.aitrip.service.UserService;
import com.aitrip.vo.WechatLoginVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/login/wechat")
    public Result<WechatLoginVO> loginWechat(@Valid @RequestBody WechatLoginRequest body,
                                             HttpServletRequest request) {
        String ip = resolveClientIp(request);
        return Result.success(userService.loginByWechat(body.getCode(), ip));
    }

    private static String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
