package com.aitrip.dto.itinerary;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ItineraryGenerateRequest {

    @NotNull(message = "userId 不能为空")
    private Long userId;

    @NotBlank(message = "攻略文本不能为空")
    private String text;

    /** 城市名称（可选，传给高德地理编码 city 参数） */
    private String city;

    /** 旅游开始日期（可选） */
    private String startDate;

    /** 旅游结束日期（可选） */
    private String endDate;

    /** 兴趣标签（可选，如 ["美食","人文","历史","自然"]） */
    private List<String> interestTags;

    /** 每日开始时间（可选，如 "09:00"） */
    private String startTime;

    /** 每日结束时间（可选，如 "21:00"） */
    private String endTime;

    /** 是否需要 AI 推荐酒店（默认 true） */
    private Boolean isGenerateHotel;
}
