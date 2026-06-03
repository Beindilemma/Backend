package com.aitrip.dto.amap;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * 高德 POI 搜索（place/text + extensions=all）解析结果。
 */
@Value
public class AmapPoiResolveResult {

    BigDecimal lng;
    BigDecimal lat;
    /** 拼接后的详细地址 */
    String address;
    /** 营业时间文案 */
    String openHours;
    /** 高德 POI 名称 */
    String poiName;
    /** 图片列表（高德 POI 图片 URL） */
    List<String> images;
    /** 联系电话 */
    String phone;

    public AmapGeoPoint toGeoPoint() {
        return new AmapGeoPoint(lng, lat);
    }

    /** 获取首张图片 */
    public String firstImage() {
        return images != null && !images.isEmpty() ? images.get(0) : null;
    }
}
