package com.aitrip.vo.itinerary;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItineraryGenerateResponse {
    private Long itineraryId;
    /** 给前端的同步耗时提示 */
    private String hint;
}
