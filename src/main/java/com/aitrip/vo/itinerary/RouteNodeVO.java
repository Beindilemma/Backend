package com.aitrip.vo.itinerary;

import com.aitrip.entity.RouteNode;
import lombok.Builder;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class RouteNodeVO {
    private Long id;
    private Long itineraryId;
    private Integer dayNumber;
    private Integer sortOrder;
    private String nodeType;
    private String spotName;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer stayTime;
    private String openHours;
    private String plannedArrival;
    private String plannedArrivalOnDate;
    private String recommendReason;
    private String imageUrl;
    private String imageSource;
    private String phone;
    private String note;
    /** 预估人均费用/门票（元） */
    private BigDecimal cost;
    private LocalDateTime createTime;

    public static RouteNodeVO from(RouteNode e) {
        return from(e, null);
    }

    public static RouteNodeVO from(RouteNode e, LocalDate calendarDateForDay) {
        String arrivalOnDate = null;
        if (calendarDateForDay != null && StringUtils.hasText(e.getPlannedArrival())) {
            arrivalOnDate = calendarDateForDay + " " + e.getPlannedArrival().trim();
        }
        return RouteNodeVO.builder()
                .id(e.getId())
                .itineraryId(e.getItineraryId())
                .dayNumber(e.getDayNumber())
                .sortOrder(e.getSortOrder())
                .nodeType(e.getNodeType())
                .spotName(e.getSpotName())
                .address(e.getAddress())
                .lat(e.getLat())
                .lng(e.getLng())
                .stayTime(e.getStayTime())
                .openHours(e.getOpenHours())
                .plannedArrival(e.getPlannedArrival())
                .plannedArrivalOnDate(arrivalOnDate)
                .recommendReason(e.getRecommendReason())
                .imageUrl(e.getImageUrl())
                .imageSource(e.getImageSource())
                .phone(e.getPhone())
                .note(e.getNote())
                .cost(e.getCost())
                .createTime(e.getCreateTime())
                .build();
    }
}
