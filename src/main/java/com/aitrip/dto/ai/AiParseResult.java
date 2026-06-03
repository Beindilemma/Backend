package com.aitrip.dto.ai;

import java.util.List;

/**
 * 大模型解析后的行程：总天数与景点节点列表。
 */
public record AiParseResult(int totalDays, List<AiParsedSpot> spots) {
}
