package com.aitrip.dto.amap;

import lombok.Value;

/**
 * @param distanceMeters 距离（米）
 * @param durationSeconds 耗时（秒）
 */
@Value
public class AmapRouteResult {
    int distanceMeters;
    int durationSeconds;
}
