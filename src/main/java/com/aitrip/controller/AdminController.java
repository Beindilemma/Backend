package com.aitrip.controller;

import com.aitrip.entity.Itinerary;
import com.aitrip.entity.User;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.ItineraryMapper;
import com.aitrip.mapper.UserMapper;
import com.aitrip.result.Result;
import com.aitrip.result.ResultCode;
import com.aitrip.service.CommunityService;
import com.aitrip.utils.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理后台接口（仅 admin 角色可访问）。
 */
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserMapper userMapper;
    private final ItineraryMapper itineraryMapper;
    private final CommunityService communityService;

    /**
     * 校验当前用户是否为 admin。
     */
    private void requireAdmin() {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        User user = userMapper.selectById(userId);
        if (user == null || user.getRole() == null || user.getRole() < 1) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    // ==================== 仪表盘统计 ====================

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        requireAdmin();
        Map<String, Object> data = new HashMap<>();

        long totalUsers = userMapper.selectCount(null);
        long totalItineraries = itineraryMapper.selectCount(null);

        // 今日新增
        LocalDate today = LocalDate.now();
        long todayUsers = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreateTime, today.atStartOfDay()));
        long todayItineraries = itineraryMapper.selectCount(
                new LambdaQueryWrapper<Itinerary>()
                        .ge(Itinerary::getCreateTime, today.atStartOfDay()));

        data.put("totalUsers", totalUsers);
        data.put("totalItineraries", totalItineraries);
        data.put("todayUsers", todayUsers);
        data.put("todayItineraries", todayItineraries);

        return Result.success(data);
    }

    // ==================== 用户管理 ====================

    @GetMapping("/users")
    public Result<Page<User>> listUsers(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        Page<User> p = userMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<User>().orderByDesc(User::getCreateTime));
        return Result.success(p);
    }

    /** 搜索用户 */
    @GetMapping("/users/search")
    public Result<List<User>> searchUsers(@RequestParam String keyword) {
        requireAdmin();
        List<User> list = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .like(User::getNickname, keyword)
                        .or().like(User::getOpenid, keyword)
                        .or().like(User::getMobile, keyword)
                        .last("LIMIT 20"));
        return Result.success(list);
    }

    /** 更新用户角色/状态 */
    @PutMapping("/users/{id}")
    public Result<Void> updateUser(@PathVariable Long id, @RequestBody User update) {
        requireAdmin();
        User user = userMapper.selectById(id);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);

        if (update.getRole() != null) user.setRole(update.getRole());
        if (update.getStatus() != null) user.setStatus(update.getStatus());
        if (update.getIsMember() != null) user.setIsMember(update.getIsMember());
        if (update.getMemberType() != null) user.setMemberType(update.getMemberType());
        if (update.getMonthlyTripLimit() != null) user.setMonthlyTripLimit(update.getMonthlyTripLimit());

        userMapper.updateById(user);
        return Result.success();
    }

    // ==================== 行程管理 ====================

    @GetMapping("/itineraries")
    public Result<Page<Itinerary>> listItineraries(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        Page<Itinerary> p = itineraryMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Itinerary>().orderByDesc(Itinerary::getCreateTime));
        return Result.success(p);
    }

    /** 删除行程 */
    @DeleteMapping("/itineraries/{id}")
    public Result<Void> deleteItinerary(@PathVariable Long id) {
        requireAdmin();
        itineraryMapper.deleteById(id);
        return Result.success();
    }

    // ==================== 内容管理 ====================

    /** 获取所有分享帖（审核用） */
    @GetMapping("/posts")
    public Result<List<CommunityService.PostVO>> listAllPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        return Result.success(communityService.listPublicPosts(page, size));
    }
}
