package com.aitrip.service;

import com.aitrip.entity.*;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.*;
import com.aitrip.result.ResultCode;
import com.aitrip.utils.UserContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 社区服务：分享路线、评论、点赞、收藏。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommunityService {

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final UserMapper userMapper;
    private final ItineraryMapper itineraryMapper;
    private final NotificationMapper notificationMapper;

    // ==================== 帖子 ====================

    /**
     * 创建分享帖。
     */
    @Transactional(rollbackFor = Exception.class)
    public Post createPost(Long userId, Long itineraryId, String content) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null) throw new BusinessException(ResultCode.ITINERARY_NOT_FOUND);
        if (!itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        // 设为公开
        if (itinerary.getIsPublic() == null || itinerary.getIsPublic() == 0) {
            itinerary.setIsPublic(1);
            itineraryMapper.updateById(itinerary);
        }

        Post post = new Post();
        post.setUserId(userId);
        post.setItineraryId(itineraryId);
        post.setContent(StringUtils.hasText(content) ? content.trim() : "");
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavCount(0);
        post.setViewCount(0);
        post.setStatus(0);
        postMapper.insert(post);
        return post;
    }

    /**
     * 获取公开帖子列表（社区首页）。
     */
    public List<PostVO> listPublicPosts(int page, int size) {
        int offset = (page - 1) * size;
        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getStatus, 0)
                        .orderByDesc(Post::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset));

        return enrichPosts(posts);
    }

    /**
     * 获取某个行程的帖子。
     */
    public PostVO getPostByItinerary(Long itineraryId) {
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>().eq(Post::getItineraryId, itineraryId));
        if (post == null) throw new BusinessException(5006, "该行程尚未分享");
        List<PostVO> vos = enrichPosts(List.of(post));
        return vos.isEmpty() ? null : vos.get(0);
    }

    private List<PostVO> enrichPosts(List<Post> posts) {
        if (posts.isEmpty()) return List.of();

        List<Long> userIds = posts.stream().map(Post::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Long> itineraryIds = posts.stream().map(Post::getItineraryId).toList();
        Map<Long, Itinerary> itineraryMap = itineraryMapper.selectBatchIds(itineraryIds).stream()
                .collect(Collectors.toMap(Itinerary::getId, i -> i, (a, b) -> a));

        Long currentUserId = UserContext.getUserId();

        return posts.stream().map(post -> {
            User user = userMap.get(post.getUserId());
            Itinerary itinerary = itineraryMap.get(post.getItineraryId());

            // 检查当前用户是否已点赞
            boolean liked = false;
            if (currentUserId != null) {
                liked = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, currentUserId)
                        .eq(Like::getTargetType, "POST")
                        .eq(Like::getTargetId, post.getId())) != null;
            }

            return new PostVO(
                    post.getId(),
                    post.getUserId(),
                    user != null ? user.getNickname() : "",
                    user != null ? user.getAvatar() : "",
                    post.getItineraryId(),
                    itinerary != null ? itinerary.getTitle() : "",
                    itinerary != null ? itinerary.getCity() : "",
                    itinerary != null ? itinerary.getTotalDays() : 0,
                    itinerary != null ? itinerary.getCoverImage() : "",
                    post.getContent(),
                    post.getLikeCount(),
                    post.getCommentCount(),
                    post.getFavCount(),
                    post.getViewCount(),
                    liked,
                    post.getCreateTime());
        }).toList();
    }

    // ==================== 评论 ====================

    /**
     * 发表评论。
     */
    @Transactional(rollbackFor = Exception.class)
    public Comment addComment(Long userId, Long postId, String content, Long parentId) {
        if (!StringUtils.hasText(content) || content.length() > 1000) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), "评论内容 1-1000 字");
        }
        Post post = postMapper.selectById(postId);
        if (post == null) throw new BusinessException(5007, "帖子不存在");

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(content.trim());
        comment.setLikeCount(0);
        comment.setStatus(0);
        commentMapper.insert(comment);

        // 更新帖子评论数
        post.setCommentCount((post.getCommentCount() != null ? post.getCommentCount() : 0) + 1);
        postMapper.updateById(post);

        // 通知帖子作者
        if (!post.getUserId().equals(userId)) {
            sendNotification(post.getUserId(), "COMMENT", "新评论",
                    "有人评论了你的分享", postId);
        }

        return comment;
    }

    /**
     * 获取帖子评论列表。
     */
    public List<CommentVO> listComments(Long postId) {
        List<Comment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .eq(Comment::getStatus, 0)
                        .orderByAsc(Comment::getCreateTime));

        if (comments.isEmpty()) return List.of();

        List<Long> userIds = comments.stream().map(Comment::getUserId).distinct().toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Long currentUserId = UserContext.getUserId();

        return comments.stream().map(c -> {
            User u = userMap.get(c.getUserId());
            boolean liked = false;
            if (currentUserId != null) {
                liked = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                        .eq(Like::getUserId, currentUserId)
                        .eq(Like::getTargetType, "COMMENT")
                        .eq(Like::getTargetId, c.getId())) != null;
            }
            return new CommentVO(
                    c.getId(), c.getPostId(), c.getUserId(),
                    u != null ? u.getNickname() : "",
                    u != null ? u.getAvatar() : "",
                    c.getParentId(), c.getContent(),
                    c.getLikeCount(), liked, c.getCreateTime());
        }).toList();
    }

    // ==================== 点赞 ====================

    /**
     * 点赞/取消点赞。
     * @return true 表示已点赞，false 表示已取消
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleLike(Long userId, String targetType, Long targetId) {
        Like existing = likeMapper.selectOne(new LambdaQueryWrapper<Like>()
                .eq(Like::getUserId, userId)
                .eq(Like::getTargetType, targetType)
                .eq(Like::getTargetId, targetId));

        if (existing != null) {
            likeMapper.deleteById(existing.getId());
            adjustCount(targetType, targetId, -1);
            return false;
        }

        Like like = new Like();
        like.setUserId(userId);
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        like.setCreateTime(LocalDateTime.now());
        likeMapper.insert(like);
        adjustCount(targetType, targetId, 1);

        // 通知
        if ("POST".equals(targetType)) {
            Post post = postMapper.selectById(targetId);
            if (post != null && !post.getUserId().equals(userId)) {
                sendNotification(post.getUserId(), "LIKE", "收到点赞",
                        "有人赞了你的分享", targetId);
            }
        }
        return true;
    }

    private void adjustCount(String targetType, Long targetId, int delta) {
        if ("POST".equals(targetType)) {
            Post post = postMapper.selectById(targetId);
            if (post != null) {
                post.setLikeCount(Math.max(0, (post.getLikeCount() != null ? post.getLikeCount() : 0) + delta));
                postMapper.updateById(post);
            }
        } else if ("COMMENT".equals(targetType)) {
            Comment comment = commentMapper.selectById(targetId);
            if (comment != null) {
                comment.setLikeCount(Math.max(0, (comment.getLikeCount() != null ? comment.getLikeCount() : 0) + delta));
                commentMapper.updateById(comment);
            }
        }
    }

    // ==================== 收藏 ====================

    @Transactional(rollbackFor = Exception.class)
    public boolean toggleFavorite(Long userId, String targetType, Long targetId) {
        Favorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getTargetType, targetType)
                .eq(Favorite::getTargetId, targetId));
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            return false;
        }
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setTargetType(targetType);
        fav.setTargetId(targetId);
        fav.setCreateTime(LocalDateTime.now());
        favoriteMapper.insert(fav);
        return true;
    }

    // ==================== 通知 ====================

    public List<Notification> listNotifications(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        return notificationMapper.selectList(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime)
                        .last("LIMIT " + size + " OFFSET " + offset));
    }

    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long notificationId) {
        Notification n = notificationMapper.selectById(notificationId);
        if (n != null) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }

    private void sendNotification(Long userId, String type, String title, String content, Long relatedId) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setRelatedId(relatedId);
        n.setIsRead(0);
        notificationMapper.insert(n);
    }

    // ==================== 内部 VO ====================

    public record PostVO(Long id, Long userId, String nickname, String avatar,
                         Long itineraryId, String title, String city, Integer totalDays,
                         String coverImage, String content, Integer likeCount,
                         Integer commentCount, Integer favCount, Integer viewCount,
                         boolean liked, LocalDateTime createTime) {}

    public record CommentVO(Long id, Long postId, Long userId, String nickname,
                            String avatar, Long parentId, String content,
                            Integer likeCount, boolean liked, LocalDateTime createTime) {}
}
