package com.aitrip.controller;

import com.aitrip.entity.Comment;
import com.aitrip.entity.Notification;
import com.aitrip.entity.Post;
import com.aitrip.exception.BusinessException;
import com.aitrip.result.Result;
import com.aitrip.result.ResultCode;
import com.aitrip.service.CommunityService;
import com.aitrip.utils.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 社区接口：分享路线、评论、点赞、收藏、通知。
 */
@RestController
@RequestMapping("/community")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    // ==================== 帖子 ====================

    /** 创建分享帖 */
    @PostMapping("/post")
    public Result<Post> createPost(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        Long itineraryId = Long.valueOf(body.get("itineraryId").toString());
        String content = (String) body.getOrDefault("content", "");
        Post post = communityService.createPost(userId, itineraryId, content);
        return Result.success(post);
    }

    /** 公开帖子列表（社区首页） */
    @GetMapping("/posts")
    public Result<List<CommunityService.PostVO>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(communityService.listPublicPosts(page, size));
    }

    /** 根据行程ID获取帖子 */
    @GetMapping("/post/itinerary/{itineraryId}")
    public Result<CommunityService.PostVO> getPostByItinerary(@PathVariable Long itineraryId) {
        return Result.success(communityService.getPostByItinerary(itineraryId));
    }

    // ==================== 评论 ====================

    /** 发表评论 */
    @PostMapping("/comment")
    public Result<Comment> addComment(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        Long postId = Long.valueOf(body.get("postId").toString());
        String content = (String) body.get("content");
        Long parentId = body.containsKey("parentId") && body.get("parentId") != null
                ? Long.valueOf(body.get("parentId").toString()) : null;
        return Result.success(communityService.addComment(userId, postId, content, parentId));
    }

    /** 获取评论列表 */
    @GetMapping("/comments/{postId}")
    public Result<List<CommunityService.CommentVO>> listComments(@PathVariable Long postId) {
        return Result.success(communityService.listComments(postId));
    }

    // ==================== 点赞 ====================

    /** 点赞/取消点赞 */
    @PostMapping("/like")
    public Result<Map<String, Object>> toggleLike(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        String targetType = (String) body.get("targetType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        boolean liked = communityService.toggleLike(userId, targetType, targetId);
        return Result.success(Map.of("liked", liked));
    }

    // ==================== 收藏 ====================

    @PostMapping("/favorite")
    public Result<Map<String, Object>> toggleFavorite(@RequestBody Map<String, Object> body) {
        Long userId = UserContext.getUserId();
        String targetType = (String) body.get("targetType");
        Long targetId = Long.valueOf(body.get("targetId").toString());
        boolean faved = communityService.toggleFavorite(userId, targetType, targetId);
        return Result.success(Map.of("favorited", faved));
    }

    // ==================== 通知 ====================

    @GetMapping("/notifications")
    public Result<List<Notification>> listNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = UserContext.getUserId();
        return Result.success(communityService.listNotifications(userId, page, size));
    }

    @GetMapping("/notifications/unread")
    public Result<Map<String, Long>> unreadCount() {
        Long userId = UserContext.getUserId();
        return Result.success(Map.of("count", communityService.unreadCount(userId)));
    }

    @PostMapping("/notifications/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        communityService.markRead(id);
        return Result.success();
    }
}
