package com.aitrip.vo.itinerary;

import lombok.Value;

/**
 * 重排节点后：已刷新当天路段数量。
 */
@Value
public class ReorderNodesResponse {
    Integer dayNumber;
    int intraDayLegsRefreshed;
}
