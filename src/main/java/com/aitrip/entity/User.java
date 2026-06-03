package com.aitrip.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("`user`")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String openid;
    private String unionid;
    private String nickname;
    private String avatar;
    private String mobile;
    /** 0 未知 1 男 2 女 */
    private Integer gender;
    /** 0 普通 1 管理员 2 超管 */
    private Integer role;
    /** 0 正常 1 禁用 */
    private Integer status;
    /** 0 非会员 1 会员 */
    private Integer isMember;
    /** 1月 2季 3年 4终身 */
    private Integer memberType;
    private LocalDateTime memberExpireTime;

    private Integer totalTripGenerate;
    private Integer monthlyTripLimit;
    private Integer currentMonthUsage;
    /** 如 2025-01 */
    private String lastResetMonth;

    private String lastLoginIp;
    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
