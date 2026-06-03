package com.aitrip.service.impl;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import com.aitrip.entity.User;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.UserMapper;
import com.aitrip.result.ResultCode;
import com.aitrip.service.UserQuotaService;
import com.aitrip.service.UserService;
import com.aitrip.utils.JwtUtils;
import com.aitrip.vo.WechatLoginVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.error.WxErrorException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int DEFAULT_MONTHLY_TRIP_LIMIT = 10;

    private final WxMaService wxMaService;
    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;
    private final UserQuotaService quotaService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WechatLoginVO loginByWechat(String code, String clientIp) {
        WxMaJscode2SessionResult session;
        try {
            session = wxMaService.getUserService().getSessionInfo(code);
        } catch (WxErrorException e) {
            log.warn("微信 code2session 失败: {}", e.getMessage());
            throw new BusinessException(ResultCode.WECHAT_CODE_INVALID.getCode(),
                    ResultCode.WECHAT_CODE_INVALID.getMessage());
        }
        if (session == null || session.getOpenid() == null) {
            throw new BusinessException(ResultCode.WECHAT_LOGIN_FAILED);
        }

        String openid = session.getOpenid();
        String unionid = session.getUnionid();

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getOpenid, openid));
        if (user == null) {
            user = new User();
            user.setOpenid(openid);
            user.setUnionid(unionid);
            user.setRole(0);
            user.setStatus(0);
            user.setTotalTripGenerate(0);
            user.setMonthlyTripLimit(DEFAULT_MONTHLY_TRIP_LIMIT);
            user.setCurrentMonthUsage(0);
            user.setIsMember(0);
            user.setLastLoginIp(clientIp);
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.insert(user);
        } else {
            if (user.getStatus() != null && user.getStatus() == 1) {
                throw new BusinessException(ResultCode.USER_DISABLED);
            }
            if (unionid != null) {
                user.setUnionid(unionid);
            }
            user.setLastLoginIp(clientIp);
            user.setLastLoginTime(LocalDateTime.now());
            userMapper.updateById(user);
        }

        String token = jwtUtils.generateToken(user.getId());
        UserQuotaService.QuotaInfo quota = quotaService.getQuotaInfo(user.getId());

        return WechatLoginVO.builder()
                .token(token)
                .userId(user.getId())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .role(user.getRole())
                .status(user.getStatus())
                .quotaLimit(quota.limit())
                .quotaUsed(quota.used())
                .quotaUnlimited(quota.unlimited())
                .build();
    }
}
