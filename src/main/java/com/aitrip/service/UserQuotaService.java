package com.aitrip.service;

import com.aitrip.entity.User;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.UserMapper;
import com.aitrip.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户分级与防滥用服务。
 * 规则：
 * - 新用户：每月 3 次生成
 * - 普通用户（注册超过1个月）：每月 10 次
 * - 会员用户（月卡/季卡/年卡）：每月 50 次
 * - 终身会员：不限次数
 * - admin：不限次数
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserQuotaService {

    private final UserMapper userMapper;

    /**
     * 检查用户是否还有生成次数，并扣减。
     * 若无配额则抛出 BusinessException。
     */
    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsume(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // admin / 超管不限次数
        if (user.getRole() != null && user.getRole() >= 1) {
            incrementTotalCount(user);
            return;
        }

        // 终身会员不限次数
        if (isLifetimeMember(user)) {
            incrementTotalCount(user);
            return;
        }

        // 检查月度重置
        String currentMonth = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        if (!currentMonth.equals(user.getLastResetMonth())) {
            // 重置月配额
            int limit = resolveMonthlyLimit(user);
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getCurrentMonthUsage, 0)
                    .set(User::getLastResetMonth, currentMonth)
                    .set(User::getMonthlyTripLimit, limit));
            user.setCurrentMonthUsage(0);
            user.setMonthlyTripLimit(limit);
        }

        // 检查是否超限
        if (user.getCurrentMonthUsage() != null && user.getMonthlyTripLimit() != null
                && user.getCurrentMonthUsage() >= user.getMonthlyTripLimit()) {
            throw new BusinessException(4001, "本月生成次数已用完（上限 " + user.getMonthlyTripLimit() + " 次），请升级会员或下月再试");
        }

        // 扣减
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("current_month_usage = current_month_usage + 1"));
        incrementTotalCount(user);
    }

    /**
     * 获取用户当前月配额信息。
     */
    public QuotaInfo getQuotaInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) return new QuotaInfo(0, 0, false);

        // 检查月度重置
        String currentMonth = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
        if (!currentMonth.equals(user.getLastResetMonth())) {
            return new QuotaInfo(resolveMonthlyLimit(user), 0, isUnlimited(user));
        }

        return new QuotaInfo(
                user.getMonthlyTripLimit() != null ? user.getMonthlyTripLimit() : 3,
                user.getCurrentMonthUsage() != null ? user.getCurrentMonthUsage() : 0,
                isUnlimited(user));
    }

    private void incrementTotalCount(User user) {
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .setSql("total_trip_generate = total_trip_generate + 1"));
    }

    private int resolveMonthlyLimit(User user) {
        if (user.getRole() != null && user.getRole() >= 1) return 9999;
        if (isLifetimeMember(user)) return 9999;
        if (isActiveMember(user)) return 50;

        // 注册超过30天为普通用户
        if (user.getCreateTime() != null
                && user.getCreateTime().plusDays(30).isBefore(LocalDateTime.now())) {
            return 10;
        }
        return 3; // 新用户
    }

    private boolean isUnlimited(User user) {
        return (user.getRole() != null && user.getRole() >= 1)
                || isLifetimeMember(user);
    }

    private boolean isLifetimeMember(User user) {
        return user.getIsMember() != null && user.getIsMember() == 1
                && user.getMemberType() != null && user.getMemberType() == 4;
    }

    private boolean isActiveMember(User user) {
        if (user.getIsMember() == null || user.getIsMember() != 1) return false;
        if (user.getMemberExpireTime() == null) return false;
        return user.getMemberExpireTime().isAfter(LocalDateTime.now());
    }

    public record QuotaInfo(int limit, int used, boolean unlimited) {
        public int remaining() { return unlimited ? 9999 : Math.max(0, limit - used); }
    }
}
