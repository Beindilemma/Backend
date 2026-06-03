package com.aitrip.vo.itinerary;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程详情 VO（平铺结构，非按天分组）。
 * 前端可自行按 dayNumber 分组。
 */
@Data
@Builder
public class ItineraryDetailVO {
    private Long id;
    private Long userId;
    private String title;
    private String city;
    private Integer totalDays;
    /** 用户填写的行程起始日（若有） */
    private LocalDate startDate;
    /** 用户填写的行程结束日（若有） */
    private LocalDate endDate;
    private BigDecimal budget;
    private Integer routeType;
    private Integer sourceType;
    private Long parentId;
    private Integer isPublic;
    private String coverImage;

    // 社区计数
    private Integer likeCount;
    private Integer favCount;
    private Integer viewCount;

    // 扩展字段
    private String interestTags;
    private String userNote;
    /** 每日开始时间 */
    private String startTime;
    /** 每日结束时间 */
    private String endTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<RouteNodeVO> nodes;
    private List<TransportVO> transports;

    /** 行程预估总花费 */
    private BigDecimal totalCost;
}
