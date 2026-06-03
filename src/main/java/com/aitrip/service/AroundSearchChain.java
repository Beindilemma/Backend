package com.aitrip.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 周边阶梯搜索链：以前一节点为圆心，逐级扩大半径检索当前景点坐标。
 * 消除全城盲搜导致的同名店误配（如茶颜悦色跨区分店）和路线折返跑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AroundSearchChain {

    private final AmapService amapService;

    /**
     * 阶梯式空间检索：5km → 10km → 全城文本兜底。
     *
     * @param currentPoiName   当前待检索 POI 名称
     * @param lastLocation     上一节点坐标 "lng,lat"（可为 null）
     * @param cityCenterLocation 城市中心坐标 "lng,lat"（lastLocation 为空时以此兜底圆心）
     * @return 最优 POI 的 "lng,lat" 坐标字符串；全链路失败返回 null
     */
    public String pickBestLocation(String currentPoiName, String lastLocation, String cityCenterLocation) {
        if (!StringUtils.hasText(currentPoiName)) {
            return null;
        }

        // 确定本次搜索的圆心基准点：优先用上一节点坐标，确保空间连续性
        String center = StringUtils.hasText(lastLocation) ? lastLocation : cityCenterLocation;
        if (!StringUtils.hasText(center)) {
            log.warn("[AroundSearchChain] 无任何基准坐标，直接兜底全城文本检索: {}", currentPoiName);
            return amapService.searchPoiText(currentPoiName);
        }

        log.debug("[AroundSearchChain] 开始阶梯检索: poi={} center={}", currentPoiName, center);

        // 第一阶梯：5 公里范围精准卡位
        String location = amapService.searchPoiAround(currentPoiName, center, 5000);
        if (location != null) {
            log.info("[AroundSearchChain] 第1阶梯(5km)命中: {} -> {}", currentPoiName, location);
            return location;
        }

        // 第二阶梯：10 公里范围弹性外拓
        location = amapService.searchPoiAround(currentPoiName, center, 10000);
        if (location != null) {
            log.info("[AroundSearchChain] 第2阶梯(10km)命中: {} -> {}", currentPoiName, location);
            return location;
        }

        // 终极兜底：全城文本检索
        location = amapService.searchPoiText(currentPoiName);
        if (location != null) {
            log.info("[AroundSearchChain] 终极兜底(全城文本)命中: {} -> {}", currentPoiName, location);
        } else {
            log.warn("[AroundSearchChain] 全链路未找到坐标: {}", currentPoiName);
        }
        return location;
    }
}
