package com.aitrip.service;

import com.aitrip.dto.itinerary.NodeOrderDTO;
import com.aitrip.dto.itinerary.ReorderNodesRequest;
import com.aitrip.dto.amap.TransportModeResult;
import com.aitrip.service.AmapService;
import com.aitrip.service.ItineraryService;
import com.aitrip.entity.Itinerary;
import com.aitrip.entity.RouteNode;
import com.aitrip.entity.Transport;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.ItineraryMapper;
import com.aitrip.mapper.RouteNodeMapper;
import com.aitrip.mapper.TransportMapper;
import com.aitrip.result.ResultCode;
import com.aitrip.vo.itinerary.ReorderNodesResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 行程节点变更：同日重排；高德路径规划与到达时间在同一事务内刷新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryNodeReplaceService {

    private static final int DAY_START_MINUTES = 9 * 60;

    private final ItineraryMapper itineraryMapper;
    private final RouteNodeMapper routeNodeMapper;
    private final TransportMapper transportMapper;
    private final AmapService amapService;
    private final ItineraryService itineraryService;

    /**
     * 场景 B：批量修改当天 sort_order 后，删除当日内部路段并重算高德路径与到达时间。
     */
    @Transactional(rollbackFor = Exception.class)
    public ReorderNodesResponse reorderNodes(Long currentUserId, Long itineraryId, ReorderNodesRequest body) {
        loadItineraryForUser(currentUserId, itineraryId);
        if (body == null || body.getOrders() == null || body.getOrders().isEmpty()) {
            throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
        }

        Integer dayNumber = body.getDayNumber();
        RouteNode first = routeNodeMapper.selectById(body.getOrders().get(0).getNodeId());
        if (first == null || !itineraryId.equals(first.getItineraryId())) {
            throw new BusinessException(ResultCode.ITINERARY_NODE_NOT_FOUND);
        }
        int day = dayNumber != null ? dayNumber : (first.getDayNumber() != null ? first.getDayNumber() : 1);

        List<RouteNode> expected = loadDayOrdered(itineraryId, day);
        Set<Long> allowedIds = expected.stream().map(RouteNode::getId).collect(Collectors.toSet());
        Set<Long> submittedIds = body.getOrders().stream().map(NodeOrderDTO::getNodeId).collect(Collectors.toSet());
        if (!submittedIds.equals(allowedIds)) {
            throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
        }
        Set<Integer> sortValues = new HashSet<>();
        for (NodeOrderDTO o : body.getOrders()) {
            sortValues.add(o.getSortOrder());
        }
        for (int i = 1; i <= expected.size(); i++) {
            if (!sortValues.contains(i)) {
                throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
            }
        }

        for (NodeOrderDTO o : body.getOrders()) {
            if (o.getNodeId() == null || o.getSortOrder() == null) {
                throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
            }
            if (!allowedIds.contains(o.getNodeId())) {
                throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
            }
            RouteNode n = routeNodeMapper.selectById(o.getNodeId());
            if (n == null || !itineraryId.equals(n.getItineraryId())
                    || !Objects.equals(day, n.getDayNumber() != null ? n.getDayNumber() : 1)) {
                throw new BusinessException(ResultCode.ITINERARY_REORDER_INVALID);
            }
            if ("HOTEL".equals(n.getNodeType())) {
                throw new BusinessException(ResultCode.ITINERARY_NODE_HOTEL_FORBIDDEN);
            }
            n.setSortOrder(o.getSortOrder());
            routeNodeMapper.updateById(n);
        }

        refreshIntraDayTransportsAndArrivals(itineraryId, day);
        itineraryService.evictDetailCache(itineraryId);
        List<RouteNode> after = loadDayOrdered(itineraryId, day);
        int legs = Math.max(0, after.size() - 1);
        return new ReorderNodesResponse(day, legs);
    }

    private Itinerary loadItineraryForUser(Long currentUserId, Long itineraryId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !itinerary.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.ITINERARY_NOT_FOUND);
        }
        return itinerary;
    }

    private RouteNode loadNodeInItinerary(Long itineraryId, Long nodeId) {
        RouteNode target = routeNodeMapper.selectById(nodeId);
        if (target == null || !itineraryId.equals(target.getItineraryId())) {
            throw new BusinessException(ResultCode.ITINERARY_NODE_NOT_FOUND);
        }
        return target;
    }

    private List<RouteNode> loadDayOrdered(Long itineraryId, int day) {
        return routeNodeMapper.selectList(
                new LambdaQueryWrapper<RouteNode>()
                        .eq(RouteNode::getItineraryId, itineraryId)
                        .eq(RouteNode::getDayNumber, day)
                        .orderByAsc(RouteNode::getSortOrder)
        );
    }

    /**
     * 删除当日「两端节点都在当天」的路段记录，按当前顺序为相邻节点对重新请求高德并写库，再重算 planned_arrival。
     */
    private void refreshIntraDayTransportsAndArrivals(Long itineraryId, int day) {
        List<RouteNode> dayNodes = loadDayOrdered(itineraryId, day);
        if (dayNodes.isEmpty()) {
            return;
        }
        Set<Long> dayIds = dayNodes.stream().map(RouteNode::getId).collect(Collectors.toSet());
        transportMapper.delete(
                new LambdaQueryWrapper<Transport>()
                        .eq(Transport::getItineraryId, itineraryId)
                        .in(Transport::getFromNodeId, dayIds)
                        .in(Transport::getToNodeId, dayIds)
        );

        for (int i = 0; i < dayNodes.size() - 1; i++) {
            insertTransportLeg(itineraryId, dayNodes.get(i), dayNodes.get(i + 1));
        }

        int startClock;
        if (day == 1) {
            Itinerary itinerary = itineraryMapper.selectById(itineraryId);
            startClock = parseHHmmToMinutes(itinerary != null ? itinerary.getStartTime() : null);
        } else {
            startClock = 9 * 60;
        }

        List<RouteNode> refreshed = loadDayOrdered(itineraryId, day);
        List<Transport> allTransports = transportMapper.selectList(
                new LambdaQueryWrapper<Transport>().eq(Transport::getItineraryId, itineraryId)
        );
        Map<String, Transport> pairMap = indexTransportByPair(allTransports);
        applyPlannedArrivals(refreshed, pairMap, startClock);
        for (RouteNode n : refreshed) {
            routeNodeMapper.updateById(n);
        }
    }

    private void insertTransportLeg(Long itineraryId, RouteNode from, RouteNode to) {
        if (!hasLngLat(from) || !hasLngLat(to)) {
            log.warn("[ItineraryNode] 跳过路段 {}→{}：缺少坐标", from.getId(), to.getId());
            return;
        }
        TransportModeResult result = amapService.determineTransportMode(
                from.getLng(), from.getLat(), to.getLng(), to.getLat());
        Transport t = new Transport();
        t.setItineraryId(itineraryId);
        t.setFromNodeId(from.getId());
        t.setToNodeId(to.getId());
        t.setMode(result.getMode().name());
        t.setDistance(result.getDistanceMeters());
        t.setDuration(result.getDurationSeconds());
        t.setPolyline(null);
        transportMapper.insert(t);
    }

    private static Map<String, Transport> indexTransportByPair(List<Transport> transports) {
        Map<String, Transport> map = new HashMap<>();
        for (Transport t : transports) {
            if (t.getFromNodeId() != null && t.getToNodeId() != null) {
                map.putIfAbsent(t.getFromNodeId() + ":" + t.getToNodeId(), t);
            }
        }
        return map;
    }

    private static void applyPlannedArrivals(List<RouteNode> dayOrdered, Map<String, Transport> pairMap, int startClockMinutes) {
        if (dayOrdered == null || dayOrdered.isEmpty()) {
            return;
        }
        int clock = startClockMinutes;
        for (int i = 0; i < dayOrdered.size(); i++) {
            RouteNode n = dayOrdered.get(i);
            n.setPlannedArrival(formatHHmm(clock));
            int stay = n.getStayTime() != null ? n.getStayTime() : 60;
            clock += stay;
            if (i < dayOrdered.size() - 1) {
                RouteNode nx = dayOrdered.get(i + 1);
                Transport leg = pairMap.get(n.getId() + ":" + nx.getId());
                if (leg != null && leg.getDuration() != null && leg.getDuration() > 0) {
                    clock += (leg.getDuration() + 59) / 60;
                }
            }
        }
    }

    private static String formatHHmm(int minutesFromMidnight) {
        int m = Math.max(0, minutesFromMidnight);
        int h = (m / 60) % 24;
        int min = m % 60;
        return String.format("%02d:%02d", h, min);
    }

    private static int parseHHmmToMinutes(String hhmm) {
        if (!StringUtils.hasText(hhmm)) return 9 * 60;
        String[] parts = hhmm.trim().split(":");
        if (parts.length != 2) return 9 * 60;
        try {
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return 9 * 60;
        }
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static boolean hasLngLat(RouteNode n) {
        return n != null && n.getLng() != null && n.getLat() != null;
    }
}
