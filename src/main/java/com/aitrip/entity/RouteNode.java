package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("t_route_node")
public class RouteNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itineraryId;
    private Integer dayNumber;
    private Integer sortOrder;

    /** 节点类型：SPOT景点 MEAL餐饮 SNACK小吃 HOTEL酒店 */
    private String nodeType;

    private String spotName;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    /** 建议停留时长（分钟） */
    private Integer stayTime;
    private String openHours;
    /** 建议到达时间，格式 HH:mm */
    private String plannedArrival;
    private String note;

    // ---- 增强字段 ----
    /** 推荐理由 */
    private String recommendReason;
    /** 图片URL */
    private String imageUrl;
    /** 图片来源：AMAP / UNSPLASH / AI */
    private String imageSource;
    /** 联系电话 */
    private String phone;
    /** 预估人均费用/门票（元） */
    private BigDecimal cost;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
