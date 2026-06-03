package com.aitrip.dto.amap;

import lombok.Value;

import java.math.BigDecimal;

@Value
public class AmapGeoPoint {
    BigDecimal lng;
    BigDecimal lat;
}
