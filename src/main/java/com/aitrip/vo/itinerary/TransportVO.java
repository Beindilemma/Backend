package com.aitrip.vo.itinerary;

import com.aitrip.entity.Transport;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TransportVO {
    private Long id;
    private Long itineraryId;
    private Long fromNodeId;
    private Long toNodeId;
    private String mode;
    private Integer distance;
    private Integer duration;
    private String description;
    private String polyline;

    public static TransportVO from(Transport e) {
        return TransportVO.builder()
                .id(e.getId())
                .itineraryId(e.getItineraryId())
                .fromNodeId(e.getFromNodeId())
                .toNodeId(e.getToNodeId())
                .mode(e.getMode())
                .distance(e.getDistance())
                .duration(e.getDuration())
                .description(e.getDescription())
                .polyline(e.getPolyline())
                .build();
    }
}
