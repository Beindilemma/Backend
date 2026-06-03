package com.aitrip.vo.itinerary;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行程列表展示用 VO。
 */
@Data
@Builder
public class ItineraryListVO {
    private Long id;
    private String title;
    private String city;
    private Integer totalDays;
    private String coverImage;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer isPublic;
    private LocalDateTime createTime;
}
