package com.aitrip.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 前端保存行程时的嵌套入参：主表信息 + 节点列表 + 交通段列表。
 * 交通段若尚未有数据库节点 ID，可用 {@link TransportItem#fromNodeIndex}/{@link TransportItem#toNodeIndex}
 * 指向 {@link #routeNodes} 列表下标，由服务端插入节点后替换为真实 from_node_id / to_node_id。
 */
@Data
public class ItineraryDTO {

    /** 创建者；若从登录态取则可不传 */
    private Long userId;

    private String title;
    private String city;
    private Integer totalDays;
    private BigDecimal budget;
    private Integer routeType;
    private Integer sourceType;
    private Long parentId;
    private Integer isPublic;
    private String coverImage;

    private List<RouteNodeItem> routeNodes;
    private List<TransportItem> transports;

    @Data
    public static class RouteNodeItem {
        private Integer dayNumber;
        private Integer sortOrder;
        private String spotName;
        private String address;
        private BigDecimal lat;
        private BigDecimal lng;
        private Integer stayTime;
        private String openHours;
        private String note;
    }

    @Data
    public static class TransportItem {
        /** 与 routeNodes 下标对应，保存前由服务层解析为 from_node_id */
        private Integer fromNodeIndex;
        /** 与 routeNodes 下标对应，保存前由服务层解析为 to_node_id */
        private Integer toNodeIndex;
        /** 若前端已持久化过节点，可直接传库中 ID，此时可忽略 index */
        private Long fromNodeId;
        private Long toNodeId;

        private String mode;
        private Integer distance;
        private Integer duration;
        private String polyline;
    }
}
