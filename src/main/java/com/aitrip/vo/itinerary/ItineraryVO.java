package com.aitrip.vo.itinerary;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 行程详情展示用 VO：按 dayNumber 分组的嵌套结构 + 路段列表。
 */
@Data
@Builder
public class ItineraryVO {

    private Long id;
    private Long userId;
    private String title;
    private String city;
    private Integer totalDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal budget;
    private Integer routeType;
    private Integer sourceType;
    private Long parentId;
    private Integer isPublic;
    private String coverImage;
    private Integer likeCount;
    private Integer favCount;
    private Integer viewCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 按天分组、天内按 sortOrder 排序 */
    private List<ItineraryDayVO> days;

    /** 全局交通路段列表 */
    private List<TransportVO> transports;

    /** 行程预估总花费（所有节点 cost 累加） */
    private BigDecimal totalCost;

    /** 剩余换一换次数 */
    private Integer replaceCount;
}
