package com.aitrip.dto.ai;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

/**
 * 与模型约定字段一致：day、name、stay_time、order（Fastjson2 反序列化）。
 */
@Data
public class AiParsedSpot {

    /** 行程第几天，从 1 开始，不得超过 total_days */
    private Integer day;

    private String name;

    @JSONField(name = "stay_time")
    private Integer stayTime;

    private Integer order;
}
