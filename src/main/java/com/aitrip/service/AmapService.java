package com.aitrip.service;

import com.aitrip.dto.amap.AmapGeoPoint;
import com.aitrip.dto.amap.AmapPoiResolveResult;
import com.aitrip.dto.amap.AmapRouteResult;
import com.aitrip.dto.amap.TransportModeResult;
import com.aitrip.exception.BusinessException;
import com.aitrip.result.ResultCode;
import com.aitrip.utils.AmapRegionSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 高德 Web 服务：POI 解析采用空间优先策略——有上一节点坐标+通勤时长时用 place/around，
 * 无空间上下文时用 AI 地址地理编码 + place/text 兜底。路径规划仍使用 direction 接口。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmapService {

    private static final String PLACE_TEXT_URL = "https://restapi.amap.com/v3/place/text";
    private static final String PLACE_AROUND_URL = "https://restapi.amap.com/v3/place/around";
    private static final String DIRECTION_WALKING_URL = "https://restapi.amap.com/v3/direction/walking";
    private static final String DIRECTION_DRIVING_URL = "https://restapi.amap.com/v3/direction/driving";
    private static final String GEOCODE_URL = "https://restapi.amap.com/v3/geocode/geo";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${amap.key}")
    private String amapKey;

    @Value("${amap.request-interval-ms:200}")
    private int amapRequestIntervalMs;

    @Value("${amap.qps-retry-max:4}")
    private int amapQpsRetryMax;

    @Value("${amap.qps-retry-backoff-ms:600}")
    private int amapQpsRetryBackoffMs;

    @Value("${amap.walk-threshold-meters:2000}")
    private int walkThresholdMeters;

    private final Object amapThrottleLock = new Object();
    private long lastAmapRequestNano;

    // ==================== 公开 API ====================

    public AmapGeoPoint geocode(String keyword, String cityLimit) {
        AmapPoiResolveResult r = resolveItineraryNodePoi(keyword, null, cityLimit,
                AmapRegionSupport.twoDigitAdcodePrefix(cityLimit), null);
        if (r == null) {
            throw new BusinessException(ResultCode.AMAP_GEOCODE_FAILED.getCode(),
                    ResultCode.AMAP_GEOCODE_FAILED.getMessage() + "：无法解析坐标");
        }
        return r.toGeoPoint();
    }

    public AmapGeoPoint geocode(String keyword) {
        return geocode(keyword, null);
    }

    public AmapPoiResolveResult resolveScenicPlace(String spotName, String cityLimit, String adcodePrefixTwo) {
        String spot = spotName.trim();
        if (!StringUtils.hasText(spot)) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), "景点名称不能为空");
        }
        String city = StringUtils.hasText(cityLimit) ? cityLimit.trim() : null;
        AmapPoiResolveResult r = resolveItineraryNodePoi(spot, null, city, adcodePrefixTwo, null);
        if (r == null) {
            throw new BusinessException(ResultCode.AMAP_GEOCODE_FAILED.getCode(),
                    ResultCode.AMAP_GEOCODE_FAILED.getMessage() + "：POI 搜索无符合置信度的结果");
        }
        return r;
    }

    /**
     * 行程生成专用：空间优先的 POI 解析。
     * <p>有上一节点坐标+通勤时长时用 place/around 在限定半径内搜索；
     * 无空间上下文时用 AI 地址地理编码 + place/text 兜底。
     *
     * @param spotName          AI 给出的地点名称
     * @param addressHint       AI 给出的详细地址
     * @param cityLimit         目标城市
     * @param adcodePrefixTwo   省级 adcode 前两位
     * @param normalizedNodeType 节点类型（SPOT/HOTEL/...）
     * @param prevLng           上一节点经度（可为 null）
     * @param prevLat           上一节点纬度（可为 null）
     * @param commuteMode       AI 给出的交通方式（步行/打车/...）
     * @param commuteDurationMin AI 给出的通勤时长（分钟）
     */
    public AmapPoiResolveResult resolveItineraryNodePoi(String spotName, String addressHint, String cityLimit,
                                                        String adcodePrefixTwo, String normalizedNodeType,
                                                        BigDecimal prevLng, BigDecimal prevLat,
                                                        String commuteMode, Integer commuteDurationMin) {
        if (!StringUtils.hasText(spotName)) return null;

        String name = spotName.trim();
        String addr = StringUtils.hasText(addressHint) ? addressHint.trim() : null;
        String city = StringUtils.hasText(cityLimit) ? cityLimit.trim() : null;
        String prefix = effectiveAdcodePrefixTwo(cityLimit, adcodePrefixTwo);

        // —— 策略1：city + address geocode（最精确） ——
        if (StringUtils.hasText(addr) && addr.length() >= 4) {
            AmapGeoPoint pt = geocodeFallback(addr, city);
            if (pt != null) {
                log.info("[Amap] address geocode: name={} addr={}", name, addr);
                return new AmapPoiResolveResult(pt.getLng(), pt.getLat(), addr, "", name, Collections.emptyList(), "");
            }
        }

        // —— 策略2：address geocode（不带 city） ——
        if (StringUtils.hasText(addr) && addr.length() >= 4) {
            AmapGeoPoint pt = geocodeFallback(addr, null);
            if (pt != null) {
                log.info("[Amap] address geocode(无city): name={} addr={}", name, addr);
                return new AmapPoiResolveResult(pt.getLng(), pt.getLat(), addr, "", name, Collections.emptyList(), "");
            }
        }

        // —— 策略3：city + name geocode（address 为空时的降级） ——
        if (StringUtils.hasText(city) && StringUtils.hasText(name)) {
            AmapGeoPoint pt = geocodeFallback(city + name, city);
            if (pt != null) {
                log.info("[Amap] city+name geocode: name={}", name);
                return new AmapPoiResolveResult(pt.getLng(), pt.getLat(),
                        city + name, "", name, Collections.emptyList(), "");
            }
        }

        // —— 策略4：place/text 取第一个城市匹配的（geocode 全失败时兜底） ——
        String types = mapNodeTypeToAmapTypes(normalizedNodeType);
        String coreName = stripParentheticalBranch(name);
        List<JsonNode> pois = fetchPlaceTextPois(coreName, city, types);
        for (JsonNode poi : pois) {
            if (hasLocation(poi) && adcodeMatches(poi.path("adcode").asText(""), prefix)) {
                log.info("[Amap] place/text兜底: name={} poi={}", name, poi.path("name").asText(""));
                return buildPoiResult(poi, poi.path("location").asText(""));
            }
        }

        return null;
    }

    /** 向后兼容：无空间上下文的调用 */
    public AmapPoiResolveResult resolveItineraryNodePoi(String spotName, String addressHint, String cityLimit,
                                                        String adcodePrefixTwo, String normalizedNodeType) {
        return resolveItineraryNodePoi(spotName, addressHint, cityLimit, adcodePrefixTwo, normalizedNodeType,
                null, null, null, null);
    }

    // ==================== POI 搜索策略 ====================

    private static String mapNodeTypeToAmapTypes(String normalizedNodeType) {
        if (!StringUtils.hasText(normalizedNodeType)) return null;
        return switch (normalizedNodeType.toUpperCase()) {
            case "BREAKFAST", "LUNCH", "DINNER", "MEAL", "SNACK" -> "餐饮服务";
            case "HOTEL" -> "住宿服务";
            case "SPOT" -> "风景名胜";
            default -> null;
        };
    }

    /** 去掉括号后缀（分店名等），提取核心名称用于搜索 */
    private static String stripParentheticalBranch(String name) {
        if (!StringUtils.hasText(name)) return name;
        String s = name.trim();
        s = s.replaceAll("（[^）]{0,30}）", "").replaceAll("\\([^)]{0,30}\\)", "").replaceAll("【[^】]{0,30}】", "");
        return s.trim().isEmpty() ? name.trim() : s.trim();
    }

    private List<JsonNode> fetchPlaceTextPois(String keywords, String cityLimit, String typesOrNull) {
        if (!StringUtils.hasText(keywords)) return List.of();
        try {
            URI uri = buildPlaceTextUri(keywords.trim(), cityLimit, typesOrNull);
            JsonNode root = getJson(uri);
            assertAmapStatusOk(root, ResultCode.AMAP_GEOCODE_FAILED);
            JsonNode pois = root.path("pois");
            if (!pois.isArray() || pois.isEmpty()) return List.of();
            List<JsonNode> out = new ArrayList<>();
            for (JsonNode p : pois) out.add(p);
            return out;
        } catch (BusinessException e) {
            if (ResultCode.AMAP_ENGINE_RESPONSE_ERROR.getCode().equals(e.getCode())) throw e;
            log.debug("[Amap] place/text 无结果: keywords={}", keywords);
            return List.of();
        } catch (Exception e) {
            log.debug("[Amap] place/text 异常: keywords={} — {}", keywords, e.getMessage());
            return List.of();
        }
    }

    // ==================== 地理编码 ====================

    public AmapGeoPoint geocodeFallback(String name, String city) {
        if (!StringUtils.hasText(name)) return null;
        try {
            URI uri = buildGeocodeUri(name, city);
            JsonNode root = getJson(uri);
            assertAmapStatusOk(root, ResultCode.AMAP_GEOCODE_FAILED);
            JsonNode geocodes = root.path("geocodes");
            if (geocodes.isArray() && !geocodes.isEmpty()) {
                String location = geocodes.get(0).path("location").asText(null);
                if (StringUtils.hasText(location)) {
                    String[] parts = location.split(",");
                    if (parts.length == 2) {
                        log.debug("geocode fallback: {} -> {}", name, location);
                        return new AmapGeoPoint(new BigDecimal(parts[0].trim()), new BigDecimal(parts[1].trim()));
                    }
                }
            }
        } catch (Exception e) {
            log.debug("geocode fallback 失败: {} - {}", name, e.getMessage());
        }
        return null;
    }

    public String getCityCenterLocation(String cityName) {
        if (!StringUtils.hasText(cityName)) return null;
        String[] candidates = buildGeocodeCandidates(cityName.trim());
        for (String candidate : candidates) {
            try {
                URI uri = buildGeocodeUri(candidate, null);
                JsonNode root = getJson(uri);
                assertAmapStatusOk(root, ResultCode.AMAP_GEOCODE_FAILED);
                JsonNode geocodes = root.path("geocodes");
                if (geocodes.isArray() && !geocodes.isEmpty()) {
                    String location = geocodes.get(0).path("location").asText(null);
                    if (StringUtils.hasText(location)) {
                        log.info("[Amap] getCityCenterLocation: {} -> {}", candidate, location);
                        return location;
                    }
                }
            } catch (Exception e) {
                log.warn("[Amap] getCityCenterLocation 失败: candidate={} — {}", candidate, e.getMessage());
            }
        }
        return null;
    }

    private static String[] buildGeocodeCandidates(String cityName) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(cityName);
        if (cityName.endsWith("市")) {
            set.add(cityName.substring(0, cityName.length() - 1));
        } else {
            set.add(cityName + "市");
        }
        if (cityName.endsWith("州") && cityName.length() > 2) {
            String noSuffix = cityName.substring(0, cityName.length() - 1);
            set.add(noSuffix);
            set.add(noSuffix + "市");
        }
        return set.toArray(new String[0]);
    }

    // ==================== 简化搜索（保留给旧调用方） ====================

    public String searchPoiAround(String keyword, String centerLocation, int radius) {
        if (!StringUtils.hasText(keyword) || !StringUtils.hasText(centerLocation)) return null;
        try {
            URI uri = buildAroundUri(keyword.trim(), centerLocation.trim(), radius, null, null);
            JsonNode root = getJson(uri);
            assertAmapStatusOk(root, ResultCode.AMAP_GEOCODE_FAILED);
            JsonNode pois = root.path("pois");
            if (pois.isArray() && !pois.isEmpty()) {
                String location = pois.get(0).path("location").asText(null);
                if (StringUtils.hasText(location)) return location;
            }
        } catch (Exception e) {
            log.warn("[Amap] searchPoiAround 异常: {}", e.getMessage());
        }
        return null;
    }

    public String searchPoiText(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        try {
            URI uri = buildSimpleTextUri(keyword.trim());
            JsonNode root = getJson(uri);
            assertAmapStatusOk(root, ResultCode.AMAP_GEOCODE_FAILED);
            JsonNode pois = root.path("pois");
            if (pois.isArray() && !pois.isEmpty()) {
                String location = pois.get(0).path("location").asText(null);
                if (StringUtils.hasText(location)) return location;
            }
        } catch (Exception e) {
            log.warn("[Amap] searchPoiText 异常: {}", e.getMessage());
        }
        return null;
    }

    // ==================== 兼容旧调用 ====================

    public AmapGeoPoint resolveScenicLngLat(String spotName, String cityLimit, String adcodePrefixTwo) {
        return resolveScenicPlace(spotName, cityLimit, adcodePrefixTwo).toGeoPoint();
    }

    public AmapGeoPoint resolveScenicLngLat(String spotName, String cityLimit) {
        return resolveScenicLngLat(spotName, cityLimit, null);
    }

    // ==================== 路径规划 ====================

    public static RouteMode smartRouteMode(int distanceMeters) {
        if (distanceMeters <= 0) return RouteMode.WALKING;
        if (distanceMeters < 2000) return RouteMode.WALKING;
        return RouteMode.DRIVING;
    }

    public AmapRouteResult planRoute(BigDecimal originLng, BigDecimal originLat,
                                     BigDecimal destLng, BigDecimal destLat, RouteMode mode) {
        if (originLng == null || originLat == null || destLng == null || destLat == null) {
            throw new BusinessException(ResultCode.VALIDATE_FAILED.getCode(), "起点或终点坐标不能为空");
        }
        String origin = formatLonLat(originLng, originLat);
        String destination = formatLonLat(destLng, destLat);
        URI uri = buildDirectionUri(origin, destination, mode);

        JsonNode root = getJson(uri);
        assertAmapStatusOk(root, ResultCode.AMAP_ROUTE_FAILED);

        JsonNode paths = root.path("route").path("paths");
        if (!paths.isArray() || paths.isEmpty()) {
            throw new BusinessException(ResultCode.AMAP_ROUTE_FAILED.getCode(),
                    ResultCode.AMAP_ROUTE_FAILED.getMessage() + "：无可用路径");
        }
        JsonNode first = paths.get(0);
        int distance = parsePositiveInt(first.path("distance"), "distance");
        int duration = parsePositiveInt(first.path("duration"), "duration");
        return new AmapRouteResult(distance, duration);
    }

    public AmapRouteResult getDirection(BigDecimal originLng, BigDecimal originLat,
                                       BigDecimal destLng, BigDecimal destLat, RouteMode mode) {
        return planRoute(originLng, originLat, destLng, destLat, mode);
    }

    public TransportModeResult determineTransportMode(BigDecimal fromLng, BigDecimal fromLat,
                                                       BigDecimal toLng, BigDecimal toLat) {
        try {
            AmapRouteResult walkRoute = getDirection(fromLng, fromLat, toLng, toLat, RouteMode.WALKING);
            if (walkRoute.getDistanceMeters() < walkThresholdMeters) {
                return new TransportModeResult(RouteMode.WALKING,
                        walkRoute.getDistanceMeters(), walkRoute.getDurationSeconds());
            }
            try {
                AmapRouteResult driveRoute = getDirection(fromLng, fromLat, toLng, toLat, RouteMode.DRIVING);
                return new TransportModeResult(RouteMode.DRIVING,
                        driveRoute.getDistanceMeters(), driveRoute.getDurationSeconds());
            } catch (Exception e) {
                log.warn("驾车路径规划失败，回退步行: {}", e.getMessage());
                return new TransportModeResult(RouteMode.WALKING,
                        walkRoute.getDistanceMeters(), walkRoute.getDurationSeconds());
            }
        } catch (Exception e) {
            log.warn("高德路径规划失败，使用 Haversine 估算: {}", e.getMessage());
            int dist = estimateDistance(fromLng, fromLat, toLng, toLat);
            int dur = (int) ((double) dist / 1.4);
            RouteMode mode = dist < walkThresholdMeters ? RouteMode.WALKING : RouteMode.DRIVING;
            return new TransportModeResult(mode, dist, dur);
        }
    }

    public static int estimateDistance(BigDecimal lng1, BigDecimal lat1,
                                        BigDecimal lng2, BigDecimal lat2) {
        if (lng1 == null || lat1 == null || lng2 == null || lat2 == null) return 0;
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return (int) Math.round(6371000 * c);
    }

    // ==================== POI 结果构建 ====================

    private AmapPoiResolveResult buildPoiResult(JsonNode poi, String location) {
        String[] parts = location.split(",");
        if (parts.length != 2) {
            throw new BusinessException(ResultCode.AMAP_GEOCODE_FAILED.getCode(),
                    ResultCode.AMAP_GEOCODE_FAILED.getMessage() + "：坐标格式无效");
        }
        BigDecimal lng, lat;
        try {
            lng = new BigDecimal(parts[0].trim());
            lat = new BigDecimal(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.AMAP_GEOCODE_FAILED.getCode(),
                    ResultCode.AMAP_GEOCODE_FAILED.getMessage() + "：坐标解析失败");
        }
        String address = formatPoiAddress(poi);
        String openHours = extractOpenHours(poi);
        String poiName = poi.path("name").asText("").trim();
        String phone = poi.path("tel").asText("").trim();

        List<String> images = new ArrayList<>();
        JsonNode photos = poi.get("photos");
        if (photos != null && photos.isArray()) {
            for (JsonNode photo : photos) {
                String url = photo.path("url").asText("");
                if (StringUtils.hasText(url)) images.add(url.trim());
            }
        }

        return new AmapPoiResolveResult(lng, lat, address, openHours, poiName, images, phone);
    }

    private static String formatPoiAddress(JsonNode poi) {
        List<String> segments = new ArrayList<>();
        appendIfPresent(segments, poi.path("pname").asText(""));
        appendIfPresent(segments, poi.path("cityname").asText(""));
        appendIfPresent(segments, poi.path("adname").asText(""));
        appendIfPresent(segments, poi.path("address").asText(""));
        if (segments.isEmpty()) {
            String name = poi.path("name").asText("").trim();
            return StringUtils.hasText(name) ? name : "";
        }
        return String.join("", segments);
    }

    private static void appendIfPresent(List<String> segments, String part) {
        if (StringUtils.hasText(part)) segments.add(part.trim());
    }

    private String extractOpenHours(JsonNode poi) {
        String business = poi.path("business").asText("").trim();
        if (StringUtils.hasText(business)) return business;
        String week = poi.path("opentime_week").asText("").trim();
        if (StringUtils.hasText(week)) return week;
        JsonNode bizExt = poi.get("biz_ext");
        if (bizExt == null || bizExt.isNull() || bizExt.isMissingNode()) return "";
        try {
            if (bizExt.isTextual()) {
                String text = bizExt.asText().trim();
                if (!StringUtils.hasText(text)) return "";
                JsonNode ext = objectMapper.readTree(text);
                return firstNonBlank(ext.path("open_time").asText(""), ext.path("opentime_week").asText(""));
            }
            if (bizExt.isObject()) {
                return firstNonBlank(bizExt.path("open_time").asText(""), bizExt.path("opentime_week").asText(""));
            }
        } catch (Exception e) {
            log.debug("解析 biz_ext 跳过: {}", e.getMessage());
        }
        return "";
    }

    private static String firstNonBlank(String a, String b) {
        if (StringUtils.hasText(a)) return a.trim();
        if (StringUtils.hasText(b)) return b.trim();
        return "";
    }

    // ==================== 辅助方法 ====================

    private static boolean hasLocation(JsonNode poi) {
        return StringUtils.hasText(poi.path("location").asText(null));
    }

    private static String formatLonLat(BigDecimal lng, BigDecimal lat) {
        return lng.stripTrailingZeros().toPlainString() + "," + lat.stripTrailingZeros().toPlainString();
    }

    private static String effectiveAdcodePrefixTwo(String cityLimit, String adcodePrefixTwo) {
        if (StringUtils.hasText(adcodePrefixTwo) && adcodePrefixTwo.length() >= 2) {
            return adcodePrefixTwo.substring(0, 2);
        }
        if (!StringUtils.hasText(cityLimit)) return null;
        String inferred = AmapRegionSupport.twoDigitAdcodePrefix(cityLimit);
        if (StringUtils.hasText(inferred) && inferred.length() >= 2) {
            return inferred.substring(0, 2);
        }
        return null;
    }

    private static boolean adcodeMatches(String adcode, String adcodePrefixTwo) {
        if (!StringUtils.hasText(adcodePrefixTwo) || adcodePrefixTwo.length() < 2) return true;
        if (!StringUtils.hasText(adcode) || adcode.length() < 2) return false;
        return adcode.regionMatches(0, adcodePrefixTwo, 0, 2);
    }

    // ==================== URI 构建 ====================

    private URI buildGeocodeUri(String address, String city) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(GEOCODE_URL)
                .queryParam("address", address)
                .queryParam("key", safeKey())
                .queryParam("output", "JSON");
        if (StringUtils.hasText(city)) b.queryParam("city", city.trim());
        return b.encode(StandardCharsets.UTF_8).build().toUri();
    }

    private URI buildPlaceTextUri(String keywords, String cityLimit, String typesOrNull) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(PLACE_TEXT_URL)
                .queryParam("keywords", keywords)
                .queryParam("key", safeKey())
                .queryParam("output", "JSON")
                .queryParam("offset", 20)
                .queryParam("page", 1)
                .queryParam("extensions", "all")
                .queryParam("citylimit", true);
        if (StringUtils.hasText(cityLimit)) b.queryParam("city", cityLimit.trim());
        if (StringUtils.hasText(typesOrNull)) b.queryParam("types", typesOrNull.trim());
        return b.encode(StandardCharsets.UTF_8).build().toUri();
    }

    private URI buildAroundUri(String keywords, String location, int radius, String cityLimit, String typesOrNull) {
        UriComponentsBuilder b = UriComponentsBuilder.fromUriString(PLACE_AROUND_URL)
                .queryParam("key", safeKey())
                .queryParam("keywords", keywords)
                .queryParam("location", location)
                .queryParam("radius", radius)
                .queryParam("sortrule", "distance")
                .queryParam("page", 1)
                .queryParam("offset", 10)
                .queryParam("extensions", "all");
        if (StringUtils.hasText(cityLimit)) b.queryParam("city", cityLimit.trim());
        if (StringUtils.hasText(typesOrNull)) b.queryParam("types", typesOrNull.trim());
        return b.encode(StandardCharsets.UTF_8).build().toUri();
    }

    private URI buildSimpleTextUri(String keywords) {
        return UriComponentsBuilder.fromUriString(PLACE_TEXT_URL)
                .queryParam("key", safeKey())
                .queryParam("keywords", keywords)
                .queryParam("output", "JSON")
                .queryParam("offset", 1)
                .queryParam("page", 1)
                .queryParam("extensions", "base")
                .queryParam("citylimit", true)
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    private URI buildDirectionUri(String origin, String destination, RouteMode mode) {
        String baseUrl = mode == RouteMode.WALKING ? DIRECTION_WALKING_URL : DIRECTION_DRIVING_URL;
        return UriComponentsBuilder.fromUriString(baseUrl)
                .queryParam("origin", origin)
                .queryParam("destination", destination)
                .queryParam("key", safeKey())
                .encode(StandardCharsets.UTF_8)
                .build()
                .toUri();
    }

    // ==================== HTTP 调用 ====================

    private String safeKey() {
        return amapKey == null ? "" : amapKey.trim();
    }

    private void throttleBeforeAmapRequest() {
        if (amapRequestIntervalMs <= 0) return;
        long minGapNanos = amapRequestIntervalMs * 1_000_000L;
        synchronized (amapThrottleLock) {
            long now = System.nanoTime();
            long waitNanos = minGapNanos - (now - lastAmapRequestNano);
            if (waitNanos > 0) sleepMillis(waitNanos / 1_000_000L);
        }
    }

    private void markAmapRequestCompleted() {
        synchronized (amapThrottleLock) {
            lastAmapRequestNano = System.nanoTime();
        }
    }

    private static void sleepMillis(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonNode getJson(URI uri) {
        log.info("[Amap] GET {}", uri);
        int maxAttempts = Math.max(0, amapQpsRetryMax);
        for (int attempt = 0; attempt <= maxAttempts; attempt++) {
            throttleBeforeAmapRequest();
            try {
                String body = restTemplate.getForObject(uri, String.class);
                markAmapRequestCompleted();
                if (!StringUtils.hasText(body)) {
                    throw new BusinessException(ResultCode.FAILED.getCode(), "高德接口返回为空");
                }
                JsonNode root = objectMapper.readTree(body);
                if ("30001".equals(root.path("infocode").asText())) {
                    throw new BusinessException(ResultCode.AMAP_ENGINE_RESPONSE_ERROR);
                }
                String status = root.path("status").asText("");
                String infocode = root.path("infocode").asText("");
                if (!"1".equals(status) && "10021".equals(infocode) && attempt < maxAttempts) {
                    log.warn("[Amap] CUQPS_HAS_EXCEEDED_THE_LIMIT (10021), backoff {}ms retry {}/{}",
                            amapQpsRetryBackoffMs, attempt + 1, maxAttempts);
                    sleepMillis(amapQpsRetryBackoffMs);
                    continue;
                }
                return root;
            } catch (BusinessException e) {
                markAmapRequestCompleted();
                throw e;
            } catch (RestClientException e) {
                markAmapRequestCompleted();
                log.warn("高德 HTTP 请求失败: {}", e.getMessage());
                throw new BusinessException(ResultCode.FAILED.getCode(), "高德接口请求失败: " + e.getMessage());
            } catch (Exception e) {
                markAmapRequestCompleted();
                log.warn("高德响应解析失败: {}", e.getMessage());
                throw new BusinessException(ResultCode.FAILED.getCode(), "高德响应解析失败: " + e.getMessage());
            }
        }
        throw new BusinessException(ResultCode.FAILED.getCode(), "高德请求异常结束");
    }

    private void assertAmapStatusOk(JsonNode root, ResultCode resultCode) {
        String status = root.path("status").asText("");
        if (!"1".equals(status)) {
            String info = root.path("info").asText("");
            String infocode = root.path("infocode").asText("");
            if ("30001".equals(infocode)) throw new BusinessException(ResultCode.AMAP_ENGINE_RESPONSE_ERROR);
            String msg = resultCode.getMessage() + "（status=" + status
                    + (StringUtils.hasText(info) ? ", info=" + info : "")
                    + (StringUtils.hasText(infocode) ? ", infocode=" + infocode : "")
                    + "）";
            throw new BusinessException(resultCode.getCode(), msg);
        }
    }

    private static int parsePositiveInt(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            throw new BusinessException(ResultCode.AMAP_ROUTE_FAILED.getCode(),
                    ResultCode.AMAP_ROUTE_FAILED.getMessage() + "：缺少" + fieldName);
        }
        try {
            int v = Integer.parseInt(node.asText());
            if (v < 0) throw new NumberFormatException();
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.AMAP_ROUTE_FAILED.getCode(),
                    ResultCode.AMAP_ROUTE_FAILED.getMessage() + "：" + fieldName + "无效");
        }
    }

    public enum RouteMode {
        WALKING,
        DRIVING
    }
}
