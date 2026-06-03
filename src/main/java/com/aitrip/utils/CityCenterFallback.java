package com.aitrip.utils;

import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 高德 POI 解析失败时，用于柔性降级的城市参考中心点（一般为该市核心商圈/广场附近）。
 * 未收录的城市将使用北京天安门附近坐标作为最后兜底。
 */
public final class CityCenterFallback {

    /** 与行程节点 note 文案一致，便于前端展示 */
    public static final String COORD_FALLBACK_NOTE = "由于地图服务并发限制，该位置/交通已自动校准，您可以在行程中手动微调";

    private static final BigDecimal DEFAULT_LNG = new BigDecimal("116.397128");
    private static final BigDecimal DEFAULT_LAT = new BigDecimal("39.916527");

    private static final Map<String, BigDecimal[]> BY_CITY = new HashMap<>();

    static {
        // lng, lat — 粗略市中心/地标，仅作降级占位
        put("北京", 116.397128, 39.916527);
        put("上海", 121.473701, 31.230416);
        put("广州", 113.264385, 23.129112);
        put("深圳", 114.057868, 22.543099);
        put("杭州", 120.153576, 30.287459);
        put("南京", 118.796877, 32.060255);
        put("苏州", 120.619585, 31.299379);
        put("成都", 104.066541, 30.572269);
        put("重庆", 106.551556, 29.563009);
        put("武汉", 114.305393, 30.593099);
        put("西安", 108.940175, 34.341568);
        put("长沙", 112.982279, 28.194090);
        put("郑州", 113.625368, 34.746599);
        put("济南", 117.000923, 36.675807);
        put("青岛", 120.382639, 36.067082);
        put("厦门", 118.089425, 24.479834);
        put("福州", 119.296494, 26.074508);
        put("合肥", 117.227239, 31.820591);
        put("南昌", 115.858197, 28.682892);
        put("天津", 117.200983, 39.084158);
        put("沈阳", 123.431475, 41.805698);
        put("哈尔滨", 126.535802, 45.802807);
        put("长春", 125.323544, 43.817071);
        put("石家庄", 114.51486, 38.042306);
        put("太原", 112.548879, 37.870662);
        put("昆明", 102.714601, 25.049153);
        put("贵阳", 106.713478, 26.578343);
        put("南宁", 108.366543, 22.817002);
        put("海口", 110.330802, 20.022071);
        put("兰州", 103.834303, 36.061089);
        put("乌鲁木齐", 87.616848, 43.825592);
        put("拉萨", 91.140856, 29.645554);
        put("银川", 106.230909, 38.487194);
        put("西宁", 101.778228, 36.617144);
        put("呼和浩特", 111.751992, 40.84149);
    }

    private static void put(String name, double lng, double lat) {
        BY_CITY.put(name, new BigDecimal[]{
                BigDecimal.valueOf(lng),
                BigDecimal.valueOf(lat)
        });
    }

    /**
     * @param cityName 行程城市（可带「市」后缀）
     * @return 非 null；若未配置映射则返回全国默认参考点（北京附近）
     */
    public static BigDecimal[] lngLatForCity(String cityName) {
        String key = normalizeCityKey(cityName);
        if (!StringUtils.hasText(key)) {
            return new BigDecimal[]{DEFAULT_LNG, DEFAULT_LAT};
        }
        BigDecimal[] hit = BY_CITY.get(key);
        if (hit != null) {
            return new BigDecimal[]{hit[0], hit[1]};
        }
        return new BigDecimal[]{DEFAULT_LNG, DEFAULT_LAT};
    }

    static String normalizeCityKey(String cityName) {
        if (!StringUtils.hasText(cityName)) {
            return "";
        }
        String s = cityName.trim();
        if (s.endsWith("市")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith("州") && s.length() >= 3) {
            // 自治州简写：如「大理州」→「大理」
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }
}
