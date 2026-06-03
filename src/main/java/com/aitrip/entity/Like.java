package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_like")
public class Like {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    /** POST / COMMENT */
    private String targetType;
    private Long targetId;
    private LocalDateTime createTime;
}
