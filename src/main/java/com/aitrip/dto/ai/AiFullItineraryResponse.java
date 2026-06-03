package com.aitrip.dto.ai;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

/**
 * AI 返回的完整行程 JSON 结构。
 * 包含总天数、起止日期，以及每日的完整计划（三餐+小吃+景点+酒店+交通）。
 */
@Data
public class AiFullItineraryResponse {

    @JSONField(name = "total_days")
    private Integer totalDays;

    /** 可选：起始日期 */
    @JSONField(name = "start_date")
    private String startDate;

    /** 每个元素是一天的完整计划 */
    @JSONField(name = "daily_plans")
    private List<AiFullDayPlan> dailyPlans;

    /** 错误码（无则不存在此字段） */
    private String error;
}
