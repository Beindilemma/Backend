package com.aitrip.dto.itinerary;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 同一天内节点顺序批量调整。
 */
@Data
public class ReorderNodesRequest {

    /**
     * 日历天；不传时从 {@link #orders} 中首个 nodeId 对应节点推断。
     */
    private Integer dayNumber;

    @NotEmpty(message = "orders 不能为空")
    @Valid
    private List<NodeOrderDTO> orders;
}
