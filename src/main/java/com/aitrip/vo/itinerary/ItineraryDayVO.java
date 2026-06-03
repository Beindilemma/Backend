package com.aitrip.vo.itinerary;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 行程中某一天的景点序列（按 sortOrder 已排好序），以及相邻景点之间的路径规划信息。
 */
@Data
@Builder
public class ItineraryDayVO {

    private Integer dayNumber;
    /** 当行程主表有 startDate 时，为该天对应的公历日期（dayNumber 从 1 起） */
    private LocalDate calendarDate;
    private List<RouteNodeVO> nodes;
    /**
     * 同一天内按游览顺序，相邻两景点之间的路程（长度 = max(0, nodes.size()-1)）。
     * 与 {@link ItineraryVO#getTransports()} 中来自数据库的全局路段可并存；此处侧重「日内相邻」展示。
     */
    private List<TransportVO> transport;
}
