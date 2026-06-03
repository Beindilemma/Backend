package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_post")
public class Post {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
    private Long itineraryId;
    private String content;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favCount;
    private Integer viewCount;
    /** 0 正常 1 屏蔽 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
