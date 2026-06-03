package com.aitrip.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WechatLoginVO {
    private String token;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer role;
    private Integer status;
    /** 每月生成上限 */
    private Integer quotaLimit;
    /** 本月已用次数 */
    private Integer quotaUsed;
    /** 是否不限次数 */
    private Boolean quotaUnlimited;
}
