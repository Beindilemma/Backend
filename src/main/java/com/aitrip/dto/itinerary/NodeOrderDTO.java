package com.aitrip.dto.itinerary;

import lombok.Data;

/**
 * 拖拽重排：节点 ID 与新的当天游览顺序。
 */
@Data
public class NodeOrderDTO {

    /** 路线节点主键 */
    private Long nodeId;
    /** 在该天内的游览顺序，从 1 连续递增 */
    private Integer sortOrder;
}
