package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("t_itinerary")
public class Itinerary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private String title;
    private String city;
    private Integer totalDays;
    private BigDecimal budget;
    /** 0 休闲 1 精简 2 深度 */
    private Integer routeType;
    /** 0 AI生成 1 手动创建 */
    private Integer sourceType;
    private Long parentId;
    /** 0 私有 1 公开 */
    private Integer isPublic;
    private String coverImage;

    // ---- 社区计数 ----
    private Integer likeCount;
    private Integer favCount;
    private Integer viewCount;

    // ---- 扩展字段 ----
    private LocalDate startDate;
    private LocalDate endDate;
    /** 兴趣标签，逗号分隔 */
    private String interestTags;
    /** 用户补充描述 */
    private String userNote;

    /** 剩余换一换次数（默认 3） */
    private Integer replaceCount;

    /** 每日开始时间，如 "09:00" */
    private String startTime;

    /** 每日结束时间，如 "21:00" */
    private String endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
