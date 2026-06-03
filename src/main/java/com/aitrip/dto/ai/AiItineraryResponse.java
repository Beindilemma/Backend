package com.aitrip.dto.ai;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class AiItineraryResponse {
    @JSONField(name = "total_days")
    private Integer totalDays;
    private List<AiParsedSpot> points;
}
