package com.aitrip;

import com.aitrip.entity.*;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.*;
import com.aitrip.service.CommunityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

/**
 * CommunityService 单元测试。
 * 使用 Mockito 模拟 Mapper 层，验证社区服务核心业务逻辑。
 */
@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock private PostMapper postMapper;
    @Mock private CommentMapper commentMapper;
    @Mock private LikeMapper likeMapper;
    @Mock private FavoriteMapper favoriteMapper;
    @Mock private UserMapper userMapper;
    @Mock private ItineraryMapper itineraryMapper;
    @Mock private NotificationMapper notificationMapper;

    @Captor private ArgumentCaptor<Post> postCaptor;
    @Captor private ArgumentCaptor<Comment> commentCaptor;
    @Captor private ArgumentCaptor<Like> likeCaptor;
    @Captor private ArgumentCaptor<Notification> notificationCaptor;

    private CommunityService communityService;

    private User testUser;
    private Itinerary testItinerary;
    private Post testPost;
    private Comment testComment;

    @BeforeEach
    void setUp() {
        communityService = new CommunityService(
                postMapper, commentMapper, likeMapper, favoriteMapper,
                userMapper, itineraryMapper, notificationMapper);

        testUser = new User();
        testUser.setId(1L);
        testUser.setNickname("测试用户");
        testUser.setAvatar("https://example.com/avatar.png");

        testItinerary = new Itinerary();
        testItinerary.setId(100L);
        testItinerary.setUserId(1L);
        testItinerary.setTitle("成都美食之旅");
        testItinerary.setCity("成都");
        testItinerary.setTotalDays(3);
        testItinerary.setIsPublic(0);

        testPost = new Post();
        testPost.setId(10L);
        testPost.setUserId(1L);
        testPost.setItineraryId(100L);
        testPost.setContent("分享我的成都之旅");
        testPost.setLikeCount(5);
        testPost.setCommentCount(2);
        testPost.setFavCount(1);
        testPost.setViewCount(100);
        testPost.setStatus(0);
        testPost.setCreateTime(LocalDateTime.now());

        testComment = new Comment();
        testComment.setId(20L);
        testComment.setPostId(10L);
        testComment.setUserId(2L);
        testComment.setContent("好棒的行程！");
        testComment.setLikeCount(0);
        testComment.setStatus(0);
        testComment.setCreateTime(LocalDateTime.now());
    }

    // ==================== createPost ====================

    @Nested
    @DisplayName("createPost 创建帖子")
    class CreatePostTest {

        @Test
        @DisplayName("创建成功：应将行程设为公开并插入帖子")
        void createPost_success() {
            given(itineraryMapper.selectById(100L)).willReturn(testItinerary);

            // 模拟 MyBatis-Plus 自动回填 ID
            given(postMapper.insert(any(Post.class))).willAnswer(inv -> {
                Post p = inv.getArgument(0);
                p.setId(10L);
                return 1;
            });

            Post result = communityService.createPost(1L, 100L, "分享我的成都之旅");

            then(itineraryMapper).should().updateById(argThat(i -> i.getIsPublic() == 1));
            then(postMapper).should().insert(postCaptor.capture());
            Post saved = postCaptor.getValue();
            assertThat(saved.getUserId()).isEqualTo(1L);
            assertThat(saved.getItineraryId()).isEqualTo(100L);
            assertThat(saved.getContent()).isEqualTo("分享我的成都之旅");
            assertThat(saved.getLikeCount()).isZero();
            assertThat(saved.getCommentCount()).isZero();
            assertThat(saved.getStatus()).isZero();
            assertThat(result.getId()).isEqualTo(testPost.getId());
        }

        @Test
        @DisplayName("创建失败：行程不存在")
        void createPost_itineraryNotFound() {
            given(itineraryMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> communityService.createPost(1L, 999L, "内容"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("行程不存在");
        }

        @Test
        @DisplayName("创建失败：无权分享他人行程")
        void createPost_forbidden() {
            given(itineraryMapper.selectById(100L)).willReturn(testItinerary);

            assertThatThrownBy(() -> communityService.createPost(2L, 100L, "内容"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("没有相关权限");
        }

        @Test
        @DisplayName("创建成功：空内容可创建")
        void createPost_emptyContent() {
            given(itineraryMapper.selectById(100L)).willReturn(testItinerary);

            communityService.createPost(1L, 100L, "");

            then(postMapper).should().insert(postCaptor.capture());
            assertThat(postCaptor.getValue().getContent()).isEmpty();
        }
    }

    // ==================== addComment ====================

    @Nested
    @DisplayName("addComment 添加评论")
    class AddCommentTest {

        @Test
        @DisplayName("评论成功：插入评论、更新计数、发送通知")
        void addComment_success() {
            given(postMapper.selectById(10L)).willReturn(testPost);

            Comment result = communityService.addComment(2L, 10L, "好棒的行程！", null);

            // 验证评论插入
            then(commentMapper).should().insert(commentCaptor.capture());
            Comment saved = commentCaptor.getValue();
            assertThat(saved.getPostId()).isEqualTo(10L);
            assertThat(saved.getUserId()).isEqualTo(2L);
            assertThat(saved.getContent()).isEqualTo("好棒的行程！");
            assertThat(saved.getStatus()).isZero();

            // 验证帖子评论数更新
            then(postMapper).should().updateById(any());
            then(postMapper).should().updateById(argThat(p ->
                    p.getCommentCount() != null && p.getCommentCount() == 3));

            // 验证通知发送
            then(notificationMapper).should().insert(notificationCaptor.capture());
            Notification note = notificationCaptor.getValue();
            assertThat(note.getUserId()).isEqualTo(1L); // 帖子作者
            assertThat(note.getType()).isEqualTo("COMMENT");
        }

        @Test
        @DisplayName("评论成功：自己评论自己的帖子不发通知")
        void addComment_self_comment_no_notification() {
            testPost.setUserId(2L); // 评论者==作者
            given(postMapper.selectById(10L)).willReturn(testPost);

            communityService.addComment(2L, 10L, "自己评论", null);

            then(notificationMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("评论失败：帖子不存在")
        void addComment_postNotFound() {
            given(postMapper.selectById(999L)).willReturn(null);

            assertThatThrownBy(() -> communityService.addComment(1L, 999L, "内容", null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("帖子不存在");
        }

        @Test
        @DisplayName("评论失败：内容为空")
        void addComment_emptyContent() {
            assertThatThrownBy(() -> communityService.addComment(1L, 10L, "", null))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("评论失败：内容超过1000字")
        void addComment_contentTooLong() {
            String longContent = "a".repeat(1001);
            assertThatThrownBy(() -> communityService.addComment(1L, 10L, longContent, null))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== toggleLike ====================

    @Nested
    @DisplayName("toggleLike 点赞/取消点赞")
    class ToggleLikeTest {

        @Test
        @DisplayName("点赞：首次点赞返回true，计数+1，发送通知")
        void toggleLike_add() {
            given(likeMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            given(postMapper.selectById(10L)).willReturn(testPost);

            boolean result = communityService.toggleLike(2L, "POST", 10L);

            assertThat(result).isTrue();
            then(likeMapper).should().insert(likeCaptor.capture());
            assertThat(likeCaptor.getValue().getUserId()).isEqualTo(2L);
            assertThat(likeCaptor.getValue().getTargetId()).isEqualTo(10L);
            // 验证计数+1
            then(postMapper).should().updateById(argThat(p ->
                    p.getLikeCount() == 6));
            // 验证通知
            then(notificationMapper).should().insert(notificationCaptor.capture());
            assertThat(notificationCaptor.getValue().getType()).isEqualTo("LIKE");
        }

        @Test
        @DisplayName("取消点赞：已点赞时取消返回false，计数-1")
        void toggleLike_remove() {
            Like existingLike = new Like();
            existingLike.setId(99L);
            existingLike.setUserId(2L);
            existingLike.setTargetType("POST");
            existingLike.setTargetId(10L);
            given(likeMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(existingLike);
            given(postMapper.selectById(10L)).willReturn(testPost);

            boolean result = communityService.toggleLike(2L, "POST", 10L);

            assertThat(result).isFalse();
            then(likeMapper).should().deleteById(99L);
            // 验证计数-1
            then(postMapper).should().updateById(argThat(p ->
                    p.getLikeCount() == 4));
            // 取消点赞不发通知
            then(notificationMapper).shouldHaveNoInteractions();
        }

        @Test
        @DisplayName("点赞自己的帖子不发通知")
        void toggleLike_self_no_notification() {
            given(likeMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);
            given(postMapper.selectById(10L)).willReturn(testPost);

            communityService.toggleLike(1L, "POST", 10L); // 1L 是帖子作者

            then(notificationMapper).shouldHaveNoInteractions();
        }
    }

    // ==================== toggleFavorite ====================

    @Nested
    @DisplayName("toggleFavorite 收藏/取消收藏")
    class ToggleFavoriteTest {

        @Test
        @DisplayName("收藏成功")
        void toggleFavorite_add() {
            given(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            boolean result = communityService.toggleFavorite(1L, "ITINERARY", 100L);

            assertThat(result).isTrue();
            then(favoriteMapper).should().insert(any(Favorite.class));
        }

        @Test
        @DisplayName("取消收藏")
        void toggleFavorite_remove() {
            Favorite fav = new Favorite();
            fav.setId(99L);
            given(favoriteMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(fav);

            boolean result = communityService.toggleFavorite(1L, "ITINERARY", 100L);

            assertThat(result).isFalse();
            then(favoriteMapper).should().deleteById(99L);
        }
    }

    // ==================== listPublicPosts ====================

    @Nested
    @DisplayName("listPublicPosts 公开帖子列表")
    class ListPublicPostsTest {

        @Test
        @DisplayName("查询成功：返回带用户信息的帖子列表")
        void listPublicPosts_success() {
            given(postMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of(testPost));
            given(userMapper.selectBatchIds(List.of(1L)))
                    .willReturn(List.of(testUser));
            given(itineraryMapper.selectBatchIds(List.of(100L)))
                    .willReturn(List.of(testItinerary));

            List<CommunityService.PostVO> result = communityService.listPublicPosts(1, 20);

            assertThat(result).hasSize(1);
            CommunityService.PostVO vo = result.get(0);
            assertThat(vo.id()).isEqualTo(10L);
            assertThat(vo.nickname()).isEqualTo("测试用户");
            assertThat(vo.city()).isEqualTo("成都");
            assertThat(vo.likeCount()).isEqualTo(5);
            assertThat(vo.commentCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("空列表：没有帖子时返回空")
        void listPublicPosts_empty() {
            given(postMapper.selectList(any(LambdaQueryWrapper.class)))
                    .willReturn(List.of());

            List<CommunityService.PostVO> result = communityService.listPublicPosts(1, 20);

            assertThat(result).isEmpty();
        }
    }

    // ==================== markRead ====================

    @Nested
    @DisplayName("markRead 标记通知已读")
    class MarkReadTest {

        @Test
        @DisplayName("标记已读成功")
        void markRead_success() {
            Notification note = new Notification();
            note.setId(50L);
            note.setIsRead(0);
            given(notificationMapper.selectById(50L)).willReturn(note);

            communityService.markRead(50L);

            then(notificationMapper).should().updateById(argThat(n -> n.getIsRead() == 1));
        }

        @Test
        @DisplayName("通知不存在时静默跳过")
        void markRead_notFound() {
            given(notificationMapper.selectById(999L)).willReturn(null);

            communityService.markRead(999L);

            then(notificationMapper).should(never()).updateById(any());
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTest {

        @Test
        @DisplayName("getPostByItinerary：行程未分享时抛异常")
        void getPostByItinerary_notFound() {
            given(postMapper.selectOne(any(LambdaQueryWrapper.class))).willReturn(null);

            assertThatThrownBy(() -> communityService.getPostByItinerary(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("尚未分享");
        }

        @Test
        @DisplayName("unreadCount：返回未读通知数")
        void unreadCount() {
            given(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).willReturn(3L);

            long count = communityService.unreadCount(1L);

            assertThat(count).isEqualTo(3L);
        }
    }
}
