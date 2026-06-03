package com.aitrip.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private Integer code;
    private String message;
    private T data;

    private Result() {}

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(ResultCode.SUCCESS.getCode());
        result.setMessage(ResultCode.SUCCESS.getMessage());
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(ResultCode resultCode) {
        return fail(resultCode.getCode(), resultCode.getMessage());
    }
}
