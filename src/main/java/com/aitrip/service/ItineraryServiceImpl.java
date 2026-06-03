package com.aitrip.service;

import com.aitrip.dto.amap.TransportModeResult;
import com.aitrip.entity.Comment;
import com.aitrip.entity.Favorite;
import com.aitrip.entity.Itinerary;
import com.aitrip.entity.Like;
import com.aitrip.entity.Post;
import com.aitrip.entity.RouteNode;
import com.aitrip.entity.Transport;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.CommentMapper;
import com.aitrip.mapper.FavoriteMapper;
import com.aitrip.mapper.ItineraryMapper;
import com.aitrip.mapper.LikeMapper;
import com.aitrip.mapper.PostMapper;
import com.aitrip.mapper.RouteNodeMapper;
import com.aitrip.mapper.TransportMapper;
import com.aitrip.result.ResultCode;
import com.aitrip.vo.itinerary.ItineraryDayVO;
import com.aitrip.vo.itinerary.ItineraryListVO;
import com.aitrip.vo.itinerary.ItineraryVO;
import com.aitrip.vo.itinerary.RouteNodeVO;
import com.aitrip.vo.itinerary.TransportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryServiceImpl implements ItineraryService {

    private static final String CACHE_KEY_PREFIX = "trip:itinerary:detail:";

    private final ItineraryMapper itineraryMapper;
    private final RouteNodeMapper routeNodeMapper;
    private final TransportMapper transportMapper;
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final LikeMapper likeMapper;
    private final FavoriteMapper favoriteMapper;
    private final AmapService amapService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${itinerary.detail-cache-ttl-seconds:600}")
    private int detailCacheTtlSeconds;

    @Override
    public ItineraryVO getDetailGrouped(Long itineraryId, Long currentUserId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !itinerary.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.ITINERARY_NOT_FOUND);
        }

        String cacheKey = CACHE_KEY_PREFIX + itineraryId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (StringUtils.hasText(cached)) {
                try {
                    return objectMapper.readValue(cached, ItineraryVO.class);
                } catch (JsonProcessingException e) {
                    log.warn("[Itinerary] Redis 详情反序列化失败，将回源 DB: {}", e.getMessage());
                    stringRedisTemplate.delete(cacheKey);
                }
            }
        } catch (Exception e) {
            log.warn("[Itinerary] Redis 读取失败，将回源 DB: {}", e.getMessage());
        }

        ItineraryVO vo = buildVoFromDb(itineraryId, itinerary);
        try {
            stringRedisTemplate.opsForValue().set(
                    cacheKey,
                    objectMapper.writeValueAsString(vo),
                    Duration.ofSeconds(Math.max(60, detailCacheTtlSeconds)));
        } catch (JsonProcessingException e) {
            log.warn("[Itinerary] 详情序列化失败（跳过写入 Redis）: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[Itinerary] Redis 写入失败（忽略缓存）: {}", e.getMessage());
        }
        return vo;
    }

    @Override
    public List<ItineraryListVO> getUserHistoryList(Long userId) {
        List<Itinerary> list = itineraryMapper.selectList(
                new LambdaQueryWrapper<Itinerary>()
                        .eq(Itinerary::getUserId, userId)
                        .orderByDesc(Itinerary::getCreateTime));
        return list.stream().map(i -> ItineraryListVO.builder()
                .id(i.getId())
                .title(i.getTitle())
                .city(i.getCity())
                .totalDays(i.getTotalDays())
                .coverImage(i.getCoverImage())
                .startDate(i.getStartDate())
                .endDate(i.getEndDate())
                .isPublic(i.getIsPublic())
                .createTime(i.getCreateTime())
                .build()).toList();
    }

    @Override
    public void evictDetailCache(Long itineraryId) {
        if (itineraryId == null) {
            return;
        }
        try {
            stringRedisTemplate.delete(CACHE_KEY_PREFIX + itineraryId);
        } catch (Exception e) {
            log.warn("[Itinerary] Redis 删除详情缓存失败: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteItinerary(Long userId, Long itineraryId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !itinerary.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.ITINERARY_NOT_FOUND);
        }

        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>().eq(Post::getItineraryId, itineraryId));
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        if (!postIds.isEmpty()) {
            List<Comment> comments = commentMapper.selectList(
                    new LambdaQueryWrapper<Comment>().in(Comment::getPostId, postIds));
            List<Long> commentIds = comments.stream().map(Comment::getId).toList();

            if (!commentIds.isEmpty()) {
                likeMapper.delete(new LambdaQueryWrapper<Like>()
                        .eq(Like::getTargetType, "COMMENT")
                        .in(Like::getTargetId, commentIds));
            }
            commentMapper.delete(
                    new LambdaQueryWrapper<Comment>().in(Comment::getPostId, postIds));

            likeMapper.delete(new LambdaQueryWrapper<Like>()
                    .eq(Like::getTargetType, "POST")
                    .in(Like::getTargetId, postIds));

            favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                    .eq(Favorite::getTargetType, "POST")
                    .in(Favorite::getTargetId, postIds));

            postMapper.delete(
                    new LambdaQueryWrapper<Post>().in(Post::getId, postIds));
        }

        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getTargetType, "ITINERARY")
                .eq(Favorite::getTargetId, itineraryId));

        transportMapper.delete(
                new LambdaQueryWrapper<Transport>().eq(Transport::getItineraryId, itineraryId));

        routeNodeMapper.delete(
                new LambdaQueryWrapper<RouteNode>().eq(RouteNode::getItineraryId, itineraryId));

        itineraryMapper.deleteById(itineraryId);

        evictDetailCache(itineraryId);
    }

    private ItineraryVO buildVoFromDb(Long itineraryId, Itinerary itinerary) {
        List<RouteNode> nodes = routeNodeMapper.selectList(
                new LambdaQueryWrapper<RouteNode>()
                        .eq(RouteNode::getItineraryId, itineraryId)
                        .orderByAsc(RouteNode::getDayNumber)
                        .orderByAsc(RouteNode::getSortOrder)
        );

        // 累加行程预估总费用
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RouteNode n : nodes) {
            if (n.getCost() != null) {
                totalCost = totalCost.add(n.getCost());
            }
        }

        List<Transport> transports = transportMapper.selectList(
                new LambdaQueryWrapper<Transport>()
                        .eq(Transport::getItineraryId, itineraryId)
        );

        Map<Long, RouteNode> nodeById = nodes.stream()
                .collect(Collectors.toMap(RouteNode::getId, n -> n, (a, b) -> a));
        transports.sort(Comparator
                .comparing((Transport t) -> nodeById.containsKey(t.getFromNodeId())
                        ? nodeById.get(t.getFromNodeId()).getDayNumber()
                        : Integer.MAX_VALUE)
                .thenComparing(t -> nodeById.containsKey(t.getFromNodeId())
                        ? nodeById.get(t.getFromNodeId()).getSortOrder()
                        : Integer.MAX_VALUE)
                .thenComparing(Transport::getId));

        Map<String, Transport> transportByPair = indexTransportsByPair(transports);

        Map<Integer, List<RouteNode>> byDay = new TreeMap<>();
        for (RouteNode n : nodes) {
            int day = n.getDayNumber() != null ? n.getDayNumber() : 1;
            byDay.computeIfAbsent(day, k -> new ArrayList<>()).add(n);
        }
        for (List<RouteNode> dayNodes : byDay.values()) {
            dayNodes.sort(Comparator.comparing(RouteNode::getSortOrder, Comparator.nullsLast(Comparator.naturalOrder())));
        }

        List<ItineraryDayVO> days = new ArrayList<>();
        LocalDate tripStart = itinerary.getStartDate();
        for (Map.Entry<Integer, List<RouteNode>> e : byDay.entrySet()) {
            int dayNum = e.getKey();
            List<RouteNode> dayNodes = e.getValue();
            final LocalDate calendarDate = tripStart != null
                    ? tripStart.plusDays(dayNum - 1L)
                    : null;
            List<RouteNodeVO> nodeVos = dayNodes.stream()
                    .map(n -> RouteNodeVO.from(n, calendarDate))
                    .toList();
            List<TransportVO> intraDay = buildIntraDayTransportLegs(dayNodes, itineraryId, transportByPair);
            days.add(ItineraryDayVO.builder()
                    .dayNumber(dayNum)
                    .calendarDate(calendarDate)
                    .nodes(nodeVos)
                    .transport(intraDay.isEmpty() ? Collections.emptyList() : intraDay)
                    .build());
        }

        return ItineraryVO.builder()
                .id(itinerary.getId())
                .userId(itinerary.getUserId())
                .title(itinerary.getTitle())
                .city(itinerary.getCity())
                .totalDays(itinerary.getTotalDays())
                .startDate(itinerary.getStartDate())
                .endDate(itinerary.getEndDate())
                .budget(itinerary.getBudget())
                .routeType(itinerary.getRouteType())
                .sourceType(itinerary.getSourceType())
                .parentId(itinerary.getParentId())
                .isPublic(itinerary.getIsPublic())
                .coverImage(itinerary.getCoverImage())
                .likeCount(itinerary.getLikeCount())
                .favCount(itinerary.getFavCount())
                .viewCount(itinerary.getViewCount())
                .createTime(itinerary.getCreateTime())
                .updateTime(itinerary.getUpdateTime())
                .days(days)
                .transports(transports.stream().map(TransportVO::from).toList())
                .totalCost(totalCost)
                .replaceCount(itinerary.getReplaceCount() != null ? itinerary.getReplaceCount() : 3)
                .build();
    }

    private static Map<String, Transport> indexTransportsByPair(List<Transport> transports) {
        Map<String, Transport> map = new HashMap<>();
        for (Transport t : transports) {
            if (t.getFromNodeId() != null && t.getToNodeId() != null) {
                map.putIfAbsent(t.getFromNodeId() + ":" + t.getToNodeId(), t);
            }
        }
        return map;
    }

    /**
     * 同一天内相邻节点：已有 t_transport → 智能判定（步行测距/驾车）。
     */
    private List<TransportVO> buildIntraDayTransportLegs(
            List<RouteNode> dayNodes,
            Long itineraryId,
            Map<String, Transport> transportByPair) {
        if (dayNodes == null || dayNodes.size() < 2) {
            return Collections.emptyList();
        }
        List<TransportVO> legs = new ArrayList<>();
        for (int i = 0; i < dayNodes.size() - 1; i++) {
            RouteNode from = dayNodes.get(i);
            RouteNode to = dayNodes.get(i + 1);
            String pairKey = from.getId() + ":" + to.getId();

            // 1. DB 已有记录，直接使用
            Transport db = transportByPair.get(pairKey);
            if (db != null) {
                legs.add(TransportVO.from(db));
                continue;
            }

            // 2. 无坐标则跳过
            if (!hasLngLat(from) || !hasLngLat(to)) {
                log.debug("[Itinerary] 日内路段缺少坐标，跳过: {} -> {}", from.getId(), to.getId());
                continue;
            }

            // 3. 智能判定交通方式（步行测距 → 超阈值切驾车），含 Haversine 回退
            try {
                TransportModeResult result = amapService.determineTransportMode(
                        from.getLng(), from.getLat(), to.getLng(), to.getLat());
                legs.add(TransportVO.builder()
                        .id(null)
                        .itineraryId(itineraryId)
                        .fromNodeId(from.getId())
                        .toNodeId(to.getId())
                        .mode(result.getMode().name())
                        .distance(result.getDistanceMeters())
                        .duration(result.getDurationSeconds())
                        .polyline(null)
                        .build());
            } catch (Exception ex) {
                log.warn("[Itinerary] 日内路径规划失败 {} -> {}: {}", from.getId(), to.getId(), ex.getMessage());
            }
        }
        return legs;
    }

    private static boolean hasLngLat(RouteNode n) {
        return n != null && n.getLng() != null && n.getLat() != null;
    }
}
