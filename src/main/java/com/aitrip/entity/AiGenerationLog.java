package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_ai_generation_log")
public class AiGenerationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long itineraryId;
    /** PENDING / PROCESSING / SUCCESS / FAILED */
    private String status;
    private Integer progress;
    private String progressMsg;
    private String requestText;
    private String city;
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
