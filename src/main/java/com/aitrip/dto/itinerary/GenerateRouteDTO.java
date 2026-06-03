package com.aitrip.dto.itinerary;

import lombok.Data;

import java.util.List;

@Data
public class GenerateRouteDTO {

    /** 城市名称 */
    private String city;

    /** 旅游开始日期，yyyy-MM-dd */
    private String startDate;

    /** 旅游结束日期，yyyy-MM-dd */
    private String endDate;

    /** 兴趣与出行风格标签 */
    private List<String> interestTags;

    /** 首日开始游玩时间，如 "14:30" */
    private String startTime;

    /** 末日结束游玩时间，如 "16:00" */
    private String endTime;

    /** 是否生成推荐酒店，默认 true */
    private Boolean isGenerateHotel = true;
}
