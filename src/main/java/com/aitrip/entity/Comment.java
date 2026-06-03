package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;
    private Long userId;
    private Long parentId;
    private String content;
    private Integer likeCount;
    /** 0 正常 1 屏蔽 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    // 非持久字段
    @TableField(exist = false)
    private String nickname;
    @TableField(exist = false)
    private String avatar;
}
