package com.aitrip.result;

import lombok.Getter;

@Getter
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "暂未登录或token已过期"),
    FORBIDDEN(403, "没有相关权限"),

    // 用户模块
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_DISABLED(1002, "用户已被禁用"),

    // 微信相关
    WECHAT_LOGIN_FAILED(2001, "微信登录失败"),
    WECHAT_CODE_INVALID(2002, "微信code无效或已过期"),

    // 高德地图
    AMAP_GEOCODE_FAILED(3001, "地理编码失败"),
    AMAP_ROUTE_FAILED(3002, "路径规划失败"),
    /** 高德 infocode=30001，多为 Key/配额/权限或请求编码问题 */
    AMAP_ENGINE_RESPONSE_ERROR(3003, "高德引擎异常(30001)，请检查 Key、Web服务权限与配额"),

    // AI 解析
    AI_PARSE_FAILED(4003, "行程文本解析失败"),
    AI_ITINERARY_INVALID_INPUT(4004, "输入与行程规划无关或无效"),
    AI_ITINERARY_INSUFFICIENT_INFO(4005, "信息不足无法规划行程"),

    // 行程
    ITINERARY_NOT_FOUND(5001, "行程不存在"),
    ITINERARY_NO_VALID_SPOTS(5002, "未解析出可定位的景点"),
    ITINERARY_NODE_NOT_FOUND(5003, "路线节点不存在或不属于该行程"),
    ITINERARY_REORDER_INVALID(5005, "节点重排参数不合法"),
    ITINERARY_NODE_HOTEL_FORBIDDEN(5007, "酒店节点不可操作");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
