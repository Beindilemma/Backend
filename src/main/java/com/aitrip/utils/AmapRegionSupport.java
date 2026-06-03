package com.aitrip.utils;

import org.springframework.util.StringUtils;

/**
 * 根据用户攻略中的地区词，推断高德行政区划 adcode 前两位，用于校验定位是否落在目标省/直辖市。
 */
public final class AmapRegionSupport {

    private AmapRegionSupport() {
    }

    /**
     * @return 两位数字字符串，如 {@code "46"}（海南）；无法识别则 {@code null}（不做 adcode 校验）。
     */
    public static String twoDigitAdcodePrefix(String regionHint) {
        if (!StringUtils.hasText(regionHint)) {
            return null;
        }
        String r = regionHint.trim();
        // 海南省 / 海南 / 岛内城市
        if (r.contains("海南") || r.contains("海口") || r.contains("三亚") || r.contains("三沙") || r.contains("儋州")
                || r.contains("文昌") || r.contains("万宁") || r.contains("陵水") || r.contains("琼海")
                || r.contains("乐东") || r.contains("保亭") || r.contains("五指山") || r.contains("琼中")
                || r.contains("澄迈") || r.contains("临高") || r.contains("定安") || r.contains("屯昌")
                || r.contains("昌江") || r.contains("白沙") || r.contains("东方")) {
            return "46";
        }
        if (r.contains("湖南") || r.contains("长沙") || r.contains("张家界") || r.contains("岳阳") || r.contains("湘潭")) {
            return "43";
        }
        if (r.contains("陕西") || r.contains("西安") || r.contains("咸阳") || r.contains("延安")) {
            return "61";
        }
        if (r.contains("北京")) {
            return "11";
        }
        if (r.contains("上海")) {
            return "31";
        }
        if (r.contains("天津")) {
            return "12";
        }
        if (r.contains("重庆")) {
            return "50";
        }
        if (r.contains("广东") || r.contains("广州") || r.contains("深圳") || r.contains("珠海") || r.contains("佛山")) {
            return "44";
        }
        if (r.contains("浙江") || r.contains("杭州") || r.contains("宁波") || r.contains("温州")) {
            return "33";
        }
        if (r.contains("江苏") || r.contains("南京") || r.contains("苏州") || r.contains("无锡")) {
            return "32";
        }
        if (r.contains("四川") || r.contains("成都") || r.contains("绵阳")) {
            return "51";
        }
        if (r.contains("云南") || r.contains("昆明") || r.contains("大理")) {
            return "53";
        }
        if (r.contains("山东") || r.contains("济南") || r.contains("青岛")) {
            return "37";
        }
        if (r.contains("河南") || r.contains("郑州") || r.contains("洛阳")) {
            return "41";
        }
        if (r.contains("湖北") || r.contains("武汉") || r.contains("宜昌")) {
            return "42";
        }
        if (r.contains("福建") || r.contains("福州") || r.contains("厦门")) {
            return "35";
        }
        if (r.contains("广西") || r.contains("南宁") || r.contains("桂林")) {
            return "45";
        }
        if (r.contains("辽宁") || r.contains("沈阳") || r.contains("大连")) {
            return "21";
        }
        if (r.contains("吉林") || r.contains("长春")) {
            return "22";
        }
        if (r.contains("黑龙江") || r.contains("哈尔滨")) {
            return "23";
        }
        if (r.contains("河北") || r.contains("石家庄") || r.contains("唐山")) {
            return "13";
        }
        if (r.contains("山西") || r.contains("太原")) {
            return "14";
        }
        if (r.contains("安徽") || r.contains("合肥") || r.contains("黄山")) {
            return "34";
        }
        if (r.contains("江西") || r.contains("南昌") || r.contains("九江")) {
            return "36";
        }
        if (r.contains("贵州") || r.contains("贵阳")) {
            return "52";
        }
        if (r.contains("甘肃") || r.contains("兰州")) {
            return "62";
        }
        if (r.contains("青海") || r.contains("西宁")) {
            return "63";
        }
        if (r.contains("宁夏") || r.contains("银川")) {
            return "64";
        }
        if (r.contains("新疆") || r.contains("乌鲁木齐")) {
            return "65";
        }
        if (r.contains("西藏") || r.contains("拉萨")) {
            return "54";
        }
        if (r.contains("内蒙古") || r.contains("呼和浩特")) {
            return "15";
        }
        return null;
    }
}
