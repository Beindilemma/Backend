package com.aitrip.dto.amap;

import com.aitrip.service.AmapService.RouteMode;
import lombok.Value;

/**
 * 智能交通方式判定结果。
 */
@Value
public class TransportModeResult {
    RouteMode mode;
    int distanceMeters;
    int durationSeconds;
}
