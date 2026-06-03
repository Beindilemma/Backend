package com.aitrip.service;

import com.aitrip.dto.ai.AiFullDayPlan;
import com.aitrip.dto.ai.AiFullItineraryResponse;
import com.aitrip.dto.itinerary.GenerateRouteDTO;
import com.aitrip.prompt.ItineraryAiPrompts;
import com.aitrip.dto.amap.AmapPoiResolveResult;
import com.aitrip.dto.amap.TransportModeResult;
import com.aitrip.entity.Itinerary;
import com.aitrip.entity.RouteNode;
import com.aitrip.entity.Transport;
import com.aitrip.exception.BusinessException;
import com.aitrip.mapper.ItineraryMapper;
import com.aitrip.mapper.RouteNodeMapper;
import com.aitrip.mapper.TransportMapper;
import com.aitrip.result.ResultCode;
import com.aitrip.utils.AmapRegionSupport;
import com.aitrip.vo.itinerary.ItineraryDetailVO;
import com.aitrip.vo.itinerary.RouteNodeVO;
import com.aitrip.vo.itinerary.TransportVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 行程业务服务：完整每日行程生成（三餐+小吃+景点+酒店+交通）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItineraryBizService {

    private static final Pattern CITY_AFTER_PREP =
            Pattern.compile("(?:去|到|在)([\\u4e00-\\u9fa5]{2,6}?)(?=玩|旅游|旅行|出差|待|住|逛|\\d|两|几|一|天|日|夜|市|县|区|镇|\\s|$)");
    private static final Pattern CITY_WITH_ADMIN_SUFFIX =
            Pattern.compile("([\\u4e00-\\u9fa5]{2,5})(?:市|州|盟|地区)");

    private final AiParserService aiParserService;
    private final AmapService amapService;
    private final ItineraryService itineraryService;
    private final ItineraryMapper itineraryMapper;
    private final RouteNodeMapper routeNodeMapper;
    private final TransportMapper transportMapper;

    /**
     * 【新】生成完整每日行程（含三餐、小吃、景点、酒店、交通、推荐理由）。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long generateFullItinerary(Long userId, String rawText, String cityHint,
                                       String startDate, String endDate,
                                       String startTime, String endTime,
                                       List<String> interestTags,
                                       Boolean isGenerateHotel) {
        return generateFullItinerary(userId, rawText, cityHint, startDate, endDate,
                startTime, endTime, interestTags, isGenerateHotel, null);
    }

    /**
     * 生成完整每日行程（含进度回调，供异步生成使用）。
     */
    public Long generateFullItinerary(Long userId, String rawText, String cityHint,
                                       String startDate, String endDate,
                                       String startTime, String endTime,
                                       List<String> interestTags,
                                       Boolean isGenerateHotel,
                                       BiConsumer<Integer, String> progressCb) {
        String regionHint = resolveGeocodeCity(cityHint, rawText);
        String cityForAi = StringUtils.hasText(regionHint) ? regionHint.trim()
                : (StringUtils.hasText(cityHint) ? cityHint.trim() : "");

        String startIso = StringUtils.hasText(startDate) ? startDate.trim() : null;
        String endIso = StringUtils.hasText(endDate) ? endDate.trim() : null;
        LocalDate parsedStart = parseOptionalIsoDate("startDate", startIso);
        LocalDate parsedEnd = parseOptionalIsoDate("endDate", endIso);
        if (parsedStart != null && parsedEnd != null && parsedEnd.isBefore(parsedStart)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), "endDate 不能早于 startDate");
        }

        // 计算用户请求的总天数
        int requestedDays = 1;
        if (parsedStart != null && parsedEnd != null) {
            requestedDays = (int) Math.max(1, ChronoUnit.DAYS.between(parsedStart, parsedEnd) + 1);
        }

        boolean needHotel = isGenerateHotel == null || isGenerateHotel;
        String aiUserContent = ItineraryAiPrompts.buildStructuredUserContent(
                cityForAi, requestedDays, startTime, endTime, needHotel, interestTags);

        // 1. AI 解析完整行程（注入 city；startTime/endTime 注入 System Prompt 铁律）
        reportProgress(progressCb, 12, "AI 正在规划每日行程...");
        AiFullItineraryResponse plan = timed("AiParserService.parseFullItinerary",
                () -> aiParserService.parseFullItinerary(aiUserContent, cityForAi, startIso, endIso, startTime, endTime, interestTags));
        reportProgress(progressCb, 30, "AI 行程规划完成，正在查询目的地信息...");
        if (plan == null || plan.getDailyPlans() == null || plan.getDailyPlans().isEmpty()) {
            throw new BusinessException(ResultCode.ITINERARY_NO_VALID_SPOTS);
        }

        // 天数上限 5 天，超出时截断
        if (plan.getTotalDays() != null && plan.getTotalDays() > 5) {
            log.warn("[Itinerary] AI 返回 totalDays={}，已截断为 5 天", plan.getTotalDays());
            plan.setTotalDays(5);
            if (plan.getDailyPlans().size() > 5) {
                plan.setDailyPlans(new ArrayList<>(plan.getDailyPlans().subList(0, 5)));
            }
        }

        String itineraryCity = StringUtils.hasText(regionHint) ? regionHint.trim()
                : (StringUtils.hasText(cityHint) ? cityHint.trim() : "");
        String amapCityLimit = StringUtils.hasText(itineraryCity) ? itineraryCity : null;
        String adcodePrefix2 = AmapRegionSupport.twoDigitAdcodePrefix(
                StringUtils.hasText(regionHint) ? regionHint : cityHint);

        // 1.5 动态城市中心降级坐标（完全替代硬编码 35 城市 CityCenterFallback）
        reportProgress(progressCb, 35, "正在获取城市位置...");
        String cityFallbackLocation = amapService.getCityCenterLocation(itineraryCity);

        // 2. 创建行程主记录
        Itinerary itinerary = new Itinerary();
        itinerary.setUserId(userId);
        itinerary.setTitle(StringUtils.hasText(rawText) ? rawText.trim() : (cityForAi + requestedDays + "日游"));
        itinerary.setCity(itineraryCity);
        itinerary.setTotalDays(plan.getTotalDays() != null ? plan.getTotalDays() : plan.getDailyPlans().size());
        itinerary.setRouteType(0);
        itinerary.setSourceType(0);
        itinerary.setIsPublic(0);
        if (parsedStart != null) {
            itinerary.setStartDate(parsedStart);
        }
        if (parsedEnd != null) {
            itinerary.setEndDate(parsedEnd);
        }
        if (interestTags != null && !interestTags.isEmpty()) {
            itinerary.setInterestTags(String.join(",", interestTags));
        }
        if (StringUtils.hasText(startTime)) {
            itinerary.setStartTime(startTime.trim());
        }
        if (StringUtils.hasText(endTime)) {
            itinerary.setEndTime(endTime.trim());
        }
        itineraryMapper.insert(itinerary);
        Long itineraryId = itinerary.getId();

        // 3. 逐天处理所有节点
        List<NodeWithGeo> allGeocoded = new ArrayList<>();
        int globalOrder = 0;

        int totalDays = plan.getDailyPlans().size();
        int dayIdx = 0;
        for (AiFullDayPlan dayPlan : plan.getDailyPlans()) {
            int dayNum = dayPlan.getDay() != null ? dayPlan.getDay() : 1;
            reportProgress(progressCb, 40 + dayIdx * 35 / totalDays,
                    "正在查询第" + dayNum + "天的地点信息...");
            dayIdx++;
            List<AiFullDayPlan.AiFullNode> nodes = dayPlan.getNodes();

            for (int i = 0; i < nodes.size(); i++) {
                AiFullDayPlan.AiFullNode n = nodes.get(i);
                String nodeName = n.getName() != null ? n.getName().trim() : "";
                if (!StringUtils.hasText(nodeName)) continue;

                globalOrder++;
                String type = normalizeNodeType(n.getType());
                String addressHint = n.getAddress();

                NodeWithGeo g = new NodeWithGeo();
                g.nodeType = type;
                g.dayNumber = dayNum;
                g.sortOrder = globalOrder;
                g.stayTime = n.getStayTime() != null ? n.getStayTime() : defaultStayTime(type);
                g.recommendReason = n.getRecommendReason() != null ? n.getRecommendReason() : "";
                g.firstOfDay = (i == 0);
                g.cost = n.getCost() != null ? n.getCost() : BigDecimal.ZERO;
                g.plannedArrival = n.getPlannedArrival();

                // 每日第一个节点作为绝对起点，清空 AI 交通数据
                if (i == 0) {
                    g.commuteMode = "";
                    g.commuteDuration = 0;
                    g.commuteDesc = "从住处出发";
                } else {
                    g.commuteMode = n.getCommuteMode();
                    g.commuteDuration = n.getCommuteDuration();
                    g.commuteDesc = n.getCommuteDesc();
                }
                g.geoFallback = false;

                // 高德 POI 解析（地址 geocode 优先）
                AmapPoiResolveResult poi = null;
                try {
                    poi = amapService.resolveItineraryNodePoi(
                            nodeName, addressHint, amapCityLimit, adcodePrefix2, type);
                } catch (Exception e) {
                    log.warn("[Itinerary] 高德解析节点[{}]失败，使用降级策略: {}", nodeName, e.getMessage());
                }

                if (poi != null) {
                    g.name = StringUtils.hasText(poi.getPoiName()) ? poi.getPoiName().trim() : nodeName;
                    g.lng = poi.getLng();
                    g.lat = poi.getLat();
                    g.address = StringUtils.hasText(poi.getAddress()) ? poi.getAddress()
                            : (StringUtils.hasText(addressHint) ? addressHint.trim() : null);
                    g.openHours = StringUtils.hasText(poi.getOpenHours()) ? poi.getOpenHours()
                            : (n.getOpenHours() != null ? n.getOpenHours() : "");
                    g.poiName = poi.getPoiName();
                    g.imageUrl = poi.firstImage();
                    g.imageSource = g.imageUrl != null ? "AMAP" : null;
                    g.phone = poi.getPhone();
                } else {
                    log.warn("[Itinerary] 高德未解析到 POI，使用动态城市中心坐标降级: spot={} city={}", nodeName, itineraryCity);
                    g.geoFallback = true;
                    g.name = nodeName;
                    if (cityFallbackLocation != null) {
                        String[] parts = cityFallbackLocation.split(",");
                        g.lng = new BigDecimal(parts[0].trim());
                        g.lat = new BigDecimal(parts[1].trim());
                    }
                    g.address = StringUtils.hasText(addressHint) ? addressHint.trim()
                            : (StringUtils.hasText(itineraryCity) ? itineraryCity + "（地点待核实）" : nodeName);
                    g.openHours = n.getOpenHours() != null ? n.getOpenHours() : "";
                    g.poiName = null;
                    g.imageUrl = StringUtils.hasText(n.getImageUrl()) ? n.getImageUrl().trim() : null;
                    g.imageSource = StringUtils.hasText(g.imageUrl) ? "AI" : null;
                    g.phone = null;
                }
                allGeocoded.add(g);

                // 高德 QPS 防抖：每次高德接口调用后强制等待 300ms
                sleepQuietly(300);
            }
        }

        if (allGeocoded.isEmpty()) {
            throw new BusinessException(ResultCode.ITINERARY_NO_VALID_SPOTS);
        }

        // 4. 批量插入节点
        List<Long> nodeIds = new ArrayList<>();
        for (NodeWithGeo g : allGeocoded) {
            RouteNode node = new RouteNode();
            node.setItineraryId(itineraryId);
            node.setDayNumber(g.dayNumber);
            node.setSortOrder(g.sortOrder);
            node.setNodeType(g.nodeType);
            node.setSpotName(g.name);
            node.setAddress(g.address);
            node.setLng(g.lng);
            node.setLat(g.lat);
            node.setStayTime(g.stayTime);
            node.setOpenHours(g.openHours);
            node.setRecommendReason(g.recommendReason);
            node.setImageUrl(g.imageUrl);
            node.setImageSource(g.imageSource);
            node.setPhone(g.phone);
            node.setCost(g.cost);
            node.setPlannedArrival(g.plannedArrival);
            node.setNote(buildRouteNodeNote(g));
            routeNodeMapper.insert(node);
            nodeIds.add(node.getId());
        }

        reportProgress(progressCb, 80, "正在规划交通路线...");

        // 5. 插入交通路段（相邻节点间 + 跨天交通接驳）
        //    跨天时，第 N 天第一个节点的起点坐标为第 N-1 天 HOTEL 的经纬度，确保行程连贯
        int pairTotal = nodeIds.size() - 1;
        for (int i = 0; i < pairTotal; i++) {
            NodeWithGeo from = allGeocoded.get(i);
            NodeWithGeo to = allGeocoded.get(i + 1);

            Long fromId = nodeIds.get(i);
            Long toId = nodeIds.get(i + 1);

            String commuteMode = "WALKING";
            int commuteDurationSec = 0;
            int commuteDistance = 0;
            String commuteDesc = null;

            // 优先使用 AI 交通数据，高德仅作兜底
            if (StringUtils.hasText(to.commuteMode)) {
                commuteMode = mapCommuteMode(to.commuteMode);
                commuteDurationSec = (to.commuteDuration != null ? to.commuteDuration : 15) * 60;
                commuteDistance = commuteDurationSec * 80 / 60;
                commuteDesc = to.commuteDesc;
            } else if (hasLngLat(from) && hasLngLat(to)) {
                TransportModeResult result = amapService.determineTransportMode(
                        from.lng, from.lat, to.lng, to.lat);
                commuteMode = result.getMode().name();
                commuteDistance = result.getDistanceMeters();
                commuteDurationSec = result.getDurationSeconds();
                commuteDesc = formatTransportDesc(result.getMode().name(), result.getDistanceMeters(), result.getDurationSeconds());
                // 高德 Direction API QPS 防抖
                sleepQuietly(300);
            }

            Transport transport = new Transport();
            transport.setItineraryId(itineraryId);
            transport.setFromNodeId(fromId);
            transport.setToNodeId(toId);
            transport.setMode(commuteMode);
            transport.setDistance(commuteDistance);
            transport.setDuration(commuteDurationSec);
            transport.setDescription(commuteDesc);
            transport.setPolyline(null);
            transportMapper.insert(transport);

            if (pairTotal > 1 && (i + 1) % Math.max(1, pairTotal / 3) == 0) {
                reportProgress(progressCb, 82 + i * 13 / pairTotal, "正在规划交通路线...");
            }
        }

        itineraryService.evictDetailCache(itineraryId);
        return itineraryId;
    }

    /**
     * 重算某行程所有节点的到达时间（无首尾约束，默认 09:00 起算）。
     */
    public void recalcPlannedArrivals(Long itineraryId) {
        recalcPlannedArrivals(itineraryId, null, null);
    }

    /**
     * 重算某行程所有节点的到达时间（自适应首日 startTime / 末日 endTime）。
     */
    public void recalcPlannedArrivals(Long itineraryId, String startTime, String endTime) {
        List<RouteNode> allNodes = routeNodeMapper.selectList(
                new LambdaQueryWrapper<RouteNode>()
                        .eq(RouteNode::getItineraryId, itineraryId)
                        .orderByAsc(RouteNode::getDayNumber)
                        .orderByAsc(RouteNode::getSortOrder));
        if (allNodes.isEmpty()) return;

        List<Transport> allTransports = transportMapper.selectList(
                new LambdaQueryWrapper<Transport>().eq(Transport::getItineraryId, itineraryId));
        Map<String, Transport> pairMap = new HashMap<>();
        for (Transport t : allTransports) {
            if (t.getFromNodeId() != null && t.getToNodeId() != null) {
                pairMap.putIfAbsent(t.getFromNodeId() + ":" + t.getToNodeId(), t);
            }
        }

        int totalDays = allNodes.stream()
                .mapToInt(n -> n.getDayNumber() != null ? n.getDayNumber() : 1)
                .max().orElse(1);

        Map<Integer, List<RouteNode>> byDay = allNodes.stream()
                .collect(Collectors.groupingBy(RouteNode::getDayNumber, HashMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<RouteNode>> entry : byDay.entrySet()) {
            int dayNum = entry.getKey();
            List<RouteNode> dayNodes = entry.getValue();
            dayNodes.sort(Comparator.comparing(RouteNode::getSortOrder));

            // 首日使用 startTime（兜底 09:00），后续天数默认 08:00（兜底锚点，实际节奏由 AI 的节点安排决定）
            int clock;
            if (dayNum == 1 && StringUtils.hasText(startTime)) {
                clock = parseHHmmToMinutes(startTime);
            } else {
                clock = 8 * 60;
            }

            for (int i = 0; i < dayNodes.size(); i++) {
                RouteNode n = dayNodes.get(i);
                n.setPlannedArrival(formatHHmm(clock));
                int stay = n.getStayTime() != null ? n.getStayTime() : defaultStayTime(n.getNodeType());
                clock += stay;

                // ★ 末日 endTime 时空超限预警
                if (dayNum == totalDays && StringUtils.hasText(endTime)) {
                    int endMin = parseHHmmToMinutes(endTime);
                    if (clock > endMin) {
                        log.warn("[Itinerary] 末日时空超限预警：第{}天 {} 节点计划完成时间 {} 超过 endTime {}，已截断",
                                dayNum, n.getSpotName(), formatHHmm(clock), endTime);
                        clock = endMin;
                    }
                }

                if (i < dayNodes.size() - 1) {
                    RouteNode nx = dayNodes.get(i + 1);
                    Transport leg = pairMap.get(n.getId() + ":" + nx.getId());
                    if (leg != null && leg.getDuration() != null && leg.getDuration() > 0) {
                        clock += (leg.getDuration() + 59) / 60; // 秒转分钟，向上取整
                    }
                }
            }
            for (RouteNode n : dayNodes) {
                routeNodeMapper.updateById(n);
            }
        }
    }

    /**
     * 将 "HH:mm" 格式字符串转为当天分钟数。
     */
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

    /**
     * 查询行程详情（平铺结构，带所有节点和交通段）。
     */
    public ItineraryDetailVO getDetail(Long itineraryId, Long currentUserId) {
        Itinerary itinerary = itineraryMapper.selectById(itineraryId);
        if (itinerary == null || !itinerary.getUserId().equals(currentUserId)) {
            throw new BusinessException(ResultCode.ITINERARY_NOT_FOUND);
        }

        List<RouteNode> nodes = routeNodeMapper.selectList(
                new LambdaQueryWrapper<RouteNode>()
                        .eq(RouteNode::getItineraryId, itineraryId)
                        .orderByAsc(RouteNode::getDayNumber)
                        .orderByAsc(RouteNode::getSortOrder));

        // 累加行程总费用
        BigDecimal totalCost = BigDecimal.ZERO;
        for (RouteNode n : nodes) {
            if (n.getCost() != null) {
                totalCost = totalCost.add(n.getCost());
            }
        }

        LocalDate tripStart = itinerary.getStartDate();
        List<RouteNodeVO> nodeVos = nodes.stream().map(n -> {
            LocalDate dayDate = null;
            if (tripStart != null && n.getDayNumber() != null) {
                dayDate = tripStart.plusDays(n.getDayNumber() - 1L);
            }
            return RouteNodeVO.from(n, dayDate);
        }).toList();

        List<Transport> transports = transportMapper.selectList(
                new LambdaQueryWrapper<Transport>()
                        .eq(Transport::getItineraryId, itineraryId));

        return ItineraryDetailVO.builder()
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
                .interestTags(itinerary.getInterestTags())
                .userNote(itinerary.getUserNote())
                .startTime(itinerary.getStartTime())
                .endTime(itinerary.getEndTime())
                .createTime(itinerary.getCreateTime())
                .updateTime(itinerary.getUpdateTime())
                .totalCost(totalCost)
                .nodes(nodeVos)
                .transports(transports.stream().map(TransportVO::from).toList())
                .build();
    }

    // =================== 动态 Prompt 拼接 ===================

    /**
     * 根据 DTO 动态构建给大模型的行程规划 Prompt。
     */
    private String buildDynamicPrompt(GenerateRouteDTO dto, int totalDays) {
        StringBuilder sb = new StringBuilder();

        // a. 基础信息
        sb.append("你是一个专业的旅游行程规划专家。请为以下行程规划一份完整的每日计划。\n\n");
        sb.append("【基础信息】\n");
        sb.append("城市：").append(dto.getCity()).append("\n");
        sb.append("总天数：").append(totalDays).append("天\n");
        if (dto.getStartDate() != null) {
            sb.append("起始日期：").append(dto.getStartDate()).append("\n");
        }
        if (dto.getEndDate() != null) {
            sb.append("结束日期：").append(dto.getEndDate()).append("\n");
        }
        sb.append("\n");

        // b. 铁律 1：首日时空切断约束（startTime 在 "14:00" 之后触发强拦截）
        if (dto.getStartTime() != null && dto.getStartTime().compareTo("14:00") > 0) {
            sb.append("【铁律 1：首日时空切断约束】\n")
              .append("由于用户第一天出发游玩时间为 ").append(dto.getStartTime())
              .append("（下午/晚上），你必须彻底切断并抹除当天 ").append(dto.getStartTime())
              .append(" 之前的所有行程（严禁生成 breakfast、lunch 节点和白天的 SPOT 景点）。\n");
            if (dto.getStartTime().compareTo("19:30") > 0) {
                sb.append("开始时间晚于 19:30，你【只允许】规划 1 个夜市/宵夜小吃节点（SNACK）和 1 个酒店入住节点（HOTEL），")
                  .append("绝对禁止安排任何需要日间门票的博物馆、寺庙和景区！");
            }
            sb.append("所有节点的 plannedArrival 必须晚于或等于 ").append(dto.getStartTime()).append("。\n\n");
        }

        // c. 铁律 2：末日截断与防折返跑约束
        if (dto.getEndTime() != null) {
            sb.append("【铁律 2：末日截断与防折返跑约束】\n")
              .append("最后一天游玩于 ").append(dto.getEndTime())
              .append(" 准时结束。你生成的最后一个节点的完成时间（plannedArrival + stayTime）绝对不能超过 ")
              .append(dto.getEndTime()).append("。\n")
              .append("【重要】最后一站推荐的商圈或伴手礼节点，其高德地理位置必须严格限制在倒数第二站的【方圆 3 公里以内】，")
              .append("绝对严禁为了买伴手礼让用户跨区域奔波超过 5 公里！\n\n");
        }

        // d. 偏好标签注入
        if (dto.getInterestTags() != null && !dto.getInterestTags().isEmpty()) {
            sb.append("【兴趣偏好】用户的兴趣标签为：")
              .append(String.join("、", dto.getInterestTags()))
              .append("。请在规划行程时充分考虑这些偏好。\n\n");
        }

        // e. 酒店开关约束
        if (Boolean.TRUE.equals(dto.getIsGenerateHotel())) {
            sb.append("【酒店要求】请在每天行程末尾推荐一家方圆2公里内的优质酒店，节点类型为 HOTEL。\n\n");
        } else {
            sb.append("【酒店要求】请绝对不要在 JSON 中生成任何 HOTEL 节点，行程在晚餐或夜市后直接结束。\n\n");
        }

        // f. 节点松绑指令
        sb.append("【节点数量要求】取消每天必须固定9个以上节点的硬性限制，请根据首尾两天的具体时间范围和游玩节奏，动态、合理地安排节点数量。严禁出现深夜排白天景点或时空重叠的现象。\n\n");

        // 输出约束（极致省 Token）
        sb.append("【输出格式（极致省 Token 约束）】必须严格按照 JSON 数组返回。")
          .append("如果因为时间过窄（如 21:00 开始玩）导致没有景点可排，那就只返回夜市和酒店节点，严禁盲目凑数！")
          .append("包含 total_days 和 daily_plans 字段。每个节点必须包含 type、name、stay_time、planned_arrival、recommend_reason、address、cost 等字段。")
          .append("planned_arrival 格式为 HH:mm，根据当天起始时间和各节点 stay_time+commute_duration 逐节点累加计算。");

        return sb.toString();
    }

    // =================== 辅助方法 ===================

    private static LocalDate parseOptionalIsoDate(String label, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), label + " 须为 yyyy-MM-dd 格式");
        }
    }

    static String resolveGeocodeCity(String explicitCity, String rawText) {
        if (StringUtils.hasText(explicitCity)) return explicitCity.trim();
        return inferCityFromRawText(rawText);
    }

    static String inferCityFromRawText(String rawText) {
        if (!StringUtils.hasText(rawText)) return null;
        Matcher m1 = CITY_AFTER_PREP.matcher(rawText);
        if (m1.find()) {
            String city = m1.group(1).trim();
            if (city.length() >= 2 && city.length() <= 6) return stripAdminSuffix(city);
        }
        Matcher m2 = CITY_WITH_ADMIN_SUFFIX.matcher(rawText);
        if (m2.find()) return stripAdminSuffix(m2.group(1).trim());
        return null;
    }

    private static String stripAdminSuffix(String name) {
        if (name.length() > 2 && name.endsWith("市")) return name.substring(0, name.length() - 1);
        return name;
    }

    private static String buildTitle(String rawText) {
        String t = rawText.replace('\n', ' ').trim();
        if (t.isEmpty()) return "智能行程";
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }

    /** 统一节点类型字符串（保留 BREAKFAST/LUNCH/DINNER 区分，供前端展示） */
    private static String normalizeNodeType(String type) {
        if (!StringUtils.hasText(type)) return "SPOT";
        String upper = type.toUpperCase();
        // Amap 检索仍按大类映射（见 AmapService.mapNodeTypeToAmapTypes）
        return switch (upper) {
            case "BREAKFAST", "LUNCH", "DINNER" -> upper; // 保留原始类型
            case "SNACK" -> "SNACK";
            case "HOTEL" -> "HOTEL";
            default -> "SPOT";
        };
    }

    /** 根据节点类型返回默认停留时长（分钟） */
    public static int defaultStayTime(String nodeType) {
        if (nodeType == null) return 60;
        return switch (nodeType) {
            case "HOTEL" -> 0;
            case "SNACK" -> 20;
            case "BREAKFAST" -> 45;
            case "LUNCH" -> 60;
            case "DINNER" -> 60;
            case "MEAL" -> 50;
            default -> 90;
        };
    }

    /** 根据交通数据生成可读描述，用于高德兜底分支（如跨天交通） */
    private static String formatTransportDesc(String mode, int distanceMeters, int durationSeconds) {
        String modeStr = switch (mode) {
            case "WALKING" -> "步行";
            case "DRIVING" -> "驾车";
            case "TRANSIT" -> "公交/地铁";
            case "BICYCLING" -> "骑行";
            default -> mode;
        };
        String distStr = distanceMeters >= 1000
                ? String.format("%.1fkm", distanceMeters / 1000.0)
                : distanceMeters + "m";
        int minutes = (durationSeconds + 59) / 60;
        return String.format("从上一站%s约%s，预计%d分钟", modeStr, distStr, minutes);
    }

    /** 将AI的中文交通方式映射为系统标准值 */
    private static String mapCommuteMode(String aiMode) {
        if (!StringUtils.hasText(aiMode)) return "WALKING";
        String mode = aiMode.trim();
        if (mode.contains("步行") || mode.contains("走")) return "WALKING";
        if (mode.contains("打车") || mode.contains("出租") || mode.contains("驾车")) return "DRIVING";
        if (mode.contains("公交") || mode.contains("巴士")) return "TRANSIT";
        if (mode.contains("骑行") || mode.contains("单车") || mode.contains("自行车")) return "BICYCLING";
        if (mode.contains("地铁") || mode.contains("轨交")) return "TRANSIT";
        return "WALKING";
    }

    private static String formatHHmm(int minutesFromMidnight) {
        int m = Math.max(0, minutesFromMidnight);
        return String.format("%02d:%02d", (m / 60) % 24, m % 60);
    }

    private static String buildRouteNodeNote(NodeWithGeo g) {
        List<String> parts = new ArrayList<>();
        if (g.geoFallback) {
            parts.add("由于地图服务并发限制，该位置/交通已自动校准，您可以在行程中手动微调");
        }
        if (!StringUtils.hasText(g.openHours)) {
            parts.add("暂无营业时间信息");
        }
        return parts.isEmpty() ? null : String.join("；", parts);
    }

    private static boolean hasLngLat(NodeWithGeo n) {
        return n != null && n.lng != null && n.lat != null;
    }

    /** 高德 QPS 防抖：固定延时确保不超过个人 Key 并发限制 */
    private static void sleepQuietly(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T timed(String label, Supplier<T> supplier) {
        long t0 = System.nanoTime();
        log.info("[Itinerary] >>> {}", label);
        try {
            T value = supplier.get();
            log.info("[Itinerary] <<< {} 成功，耗时 {} ms", label, (System.nanoTime() - t0) / 1_000_000L);
            return value;
        } catch (Exception e) {
            log.info("[Itinerary] <<< {} 失败，耗时 {} ms — {}", label, (System.nanoTime() - t0) / 1_000_000L, e.getMessage());
            throw e;
        }
    }

    // =================== 内部数据结构 ===================

    private static final class NodeWithGeo {
        private String name;
        private String nodeType;
        private Integer dayNumber;
        private Integer sortOrder;
        private Integer stayTime;
        private BigDecimal lng;
        private BigDecimal lat;
        private String address;
        private String openHours;
        private String poiName;
        private String recommendReason;
        private String commuteMode;
        private Integer commuteDuration;
        private String commuteDesc;
        private String imageUrl;
        private String imageSource;
        private String phone;
        /** AI 生成的预估人均费用/门票（元） */
        private BigDecimal cost;
        /** AI 生成的建议到达时间 HH:mm */
        private String plannedArrival;
        /** true 表示未匹配到高德 POI，已使用城市参考中心坐标 */
        private boolean geoFallback;
        /** 是否为当天的第一个节点 */
        private boolean firstOfDay;
    }

    private static void reportProgress(BiConsumer<Integer, String> cb, int pct, String msg) {
        if (cb != null) {
            cb.accept(pct, msg);
        }
    }
}
