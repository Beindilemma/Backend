package com.aitrip.service;

import com.aitrip.vo.itinerary.ItineraryListVO;
import com.aitrip.vo.itinerary.ItineraryVO;

import java.util.List;

/**
 * 行程查询与详情组装（含按天分组、缓存、历史列表）。
 */
public interface ItineraryService {

    /**
     * 校验归属后，查询行程及全部节点，按 dayNumber 分组并排序，封装为 ItineraryVO。
     * 优先读 Redis 缓存，未命中则查库组装并写入缓存。
     */
    ItineraryVO getDetailGrouped(Long itineraryId, Long currentUserId);

    /** 清除详情缓存。 */
    void evictDetailCache(Long itineraryId);

    /** 获取用户的历史行程列表。 */
    List<ItineraryListVO> getUserHistoryList(Long userId);

    /**
     * 删除行程（级联删除节点、交通、社区帖子、评论、点赞、收藏）。
     * 仅行程所有者可删除。
     */
    void deleteItinerary(Long userId, Long itineraryId);
}
