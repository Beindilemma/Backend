package com.aitrip.config;

import com.aitrip.result.Result;
import com.aitrip.result.ResultCode;
import com.aitrip.utils.JwtUtils;
import com.aitrip.utils.UserContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = resolveToken(request);
        if (!StringUtils.hasText(token) || !jwtUtils.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(objectMapper.writeValueAsString(Result.fail(ResultCode.UNAUTHORIZED)));
            return false;
        }
        Long userId = jwtUtils.getUserIdFromToken(token);
        UserContext.setUserId(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
