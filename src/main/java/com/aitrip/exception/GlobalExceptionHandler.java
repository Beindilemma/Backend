package com.aitrip.exception;

import com.aitrip.result.Result;
import com.aitrip.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, detail={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), friendlyBusinessMessage(e));
    }

    private static String friendlyBusinessMessage(BusinessException e) {
        Integer c = e.getCode();
        if (c == null) {
            return e.getMessage();
        }
        if (c.equals(ResultCode.AI_PARSE_FAILED.getCode())) {
            return "AI 正在思考中，请稍后再试";
        }
        if (c.equals(ResultCode.AMAP_GEOCODE_FAILED.getCode()) || c.equals(ResultCode.AMAP_ROUTE_FAILED.getCode())) {
            return "地图服务繁忙或请求超限，请稍后再试";
        }
        if (c.equals(ResultCode.ITINERARY_NOT_FOUND.getCode())) {
            return "行程不存在或无权查看";
        }
        if (c.equals(ResultCode.ITINERARY_NO_VALID_SPOTS.getCode())) {
            return "未能从文本中识别出可生成行程的景点，请换一段描述试试";
        }
        if (c.equals(ResultCode.FORBIDDEN.getCode())) {
            return "没有权限执行此操作";
        }
        return e.getMessage();
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public Result<?> handleValidException(Exception e) {
        String message = e instanceof MethodArgumentNotValidException ex
                ? ex.getBindingResult().getFieldErrors().stream()
                    .map(err -> err.getField() + ": " + err.getDefaultMessage())
                    .findFirst().orElse("参数校验失败")
                : e.getMessage();
        log.warn("参数校验失败: {}", message);
        return Result.fail(ResultCode.VALIDATE_FAILED.getCode(), message);
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.FAILED);
    }
}
