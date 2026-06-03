package com.aitrip.dto.ai;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 返回的完整每日计划：三餐+小吃+景点+酒店，含推荐理由与交通建议。
 */
@Data
public class AiFullDayPlan {

    /** 第几天，从1开始 */
    private Integer day;

    /** 当天所有节点（按时间顺序排列：早餐→小吃→景点→午餐→小吃→景点→晚餐→小吃→酒店） */
    private List<AiFullNode> nodes;

    @Data
    public static class AiFullNode {

        /** 节点类型：BREAKFAST / LUNCH / DINNER / SNACK / SPOT / HOTEL */
        private String type;

        /** 名称 */
        private String name;

        /** 建议停留时长（分钟） */
        @JSONField(name = "stay_time")
        private Integer stayTime;

        /** 推荐理由 */
        @JSONField(name = "recommend_reason")
        private String recommendReason;

        /** 图片URL（AI 若能提供最好，否则后端用高德 POI 补全） */
        @JSONField(name = "image_url")
        private String imageUrl;

        /** 从上个节点到本节点的交通方式，如"步行""地铁""打车" */
        @JSONField(name = "commute_mode")
        private String commuteMode;

        /** 从上个节点到本节点的耗时（分钟） */
        @JSONField(name = "commute_duration")
        private Integer commuteDuration;

        /** 交通描述，如"步行约800米到地铁站，乘坐2号线到鼓楼站" */
        @JSONField(name = "commute_desc")
        private String commuteDesc;

        /** 地址（可选，AI 若知道可填） */
        private String address;

        /** 经度（AI 若知道可填） */
        private BigDecimal lng;

        /** 纬度（AI 若知道可填） */
        private BigDecimal lat;

        /** 营业时间（餐饮/景点） */
        @JSONField(name = "open_hours")
        private String openHours;

        /** 参考价格（可选） */
        private String price;

        /** 预估人均费用/门票（元），AI 生成 */
        @JSONField(name = "cost")
        private BigDecimal cost;

        /** 建议到达时间，格式 HH:mm，AI 根据当天节奏自主计算 */
        @JSONField(name = "planned_arrival")
        private String plannedArrival;
    }
}
