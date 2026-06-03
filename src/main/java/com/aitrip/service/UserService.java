package com.aitrip.service;

import com.aitrip.vo.WechatLoginVO;

public interface UserService {

    WechatLoginVO loginByWechat(String code, String clientIp);
}
