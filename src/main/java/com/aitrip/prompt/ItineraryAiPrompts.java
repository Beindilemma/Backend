package com.aitrip.prompt;

import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 行程生成相关的 System Prompt 拼装。
 * 使用动态变量 ${city} 替换，彻底移除硬编码城市 Few-Shot 示例，防止跨城幻觉。
 */
public final class ItineraryAiPrompts {

    private ItineraryAiPrompts() {
    }

    /**
     * 完整行程规划的 System Prompt。
     *
     * @param cityParam    请求中的城市参数（已 trim）；为空时表示未显式指定，由模型仅从用户原文推断唯一目的地
     * @param interestTags 用户偏好标签（中文描述列表），注入 System Prompt 引导 AI 按偏好规划
     */
    public static String buildFullItinerarySystemPrompt(String cityParam, String startTime, String endTime,
                                                         List<String> interestTags) {
        String city = StringUtils.hasText(cityParam) ? cityParam.trim() : "目标城市";

        String base = """
                你是一个专业的旅游行程规划专家。请根据用户的需求，规划一份完整的每日行程。

                【核心规则】
                1. 意图检查：如果输入与旅游无关（打招呼、闲聊、无意义符号），输出 {"error":"INVALID_INPUT"}
                2. 信息不足：如果无法识别目的地或天数，输出 {"error":"INSUFFICIENT_INFO"}
                3. 天数限制：上限为 5天。如果用户输入的出游天数超过 5 天（例如"去北京玩 10 天"），你必须自动将天数截断为 5 天进行规划，并在最后一天最后一个节点的推荐理由中友好提示："由于单次规划策略限制，已为您精选前 5 天的深度游玩路线。"
                4. 时间边界规则与弹性节奏（重要）：根据实际可用时间动态合理安排节点数量。
                   - 【首日硬边界】：仅第 1 天受起始时间 ${startTime} 约束——你【绝对禁止】在第 1 天生成此时间之前的任何行程，所有首日节点的计划开始时间必须晚于或等于 ${startTime}。如果起始时间晚于 19:30，你【只允许】规划 1 个晚间特色消夜/夜市节点（SNACK）和 1 个酒店下榻节点（HOTEL），严禁硬凑白天景点。
                   - 【末日硬边界】：仅最后 1 天受结束时间 ${endTime} 约束——最后一天最后一个节点的结束时间绝对不能超过 ${endTime}。
                   - 【中间日自由发挥（关键）】：第 2 天到倒数第 2 天完全不受 ${startTime} 或 ${endTime} 的限制。你必须根据用户偏好和当天景点特点，自由、多样化地安排每天的起止时间与节奏。核心原则：远郊大景点早出晚归，市区休闲日晚出或晚归，紧凑日与轻松日交替，严禁所有天同一节奏。
                5. 每个节点必须附带推荐理由（recommend_reason），说明为什么推荐这里。
                6. 从第二个节点开始，每个节点必须附带交通方式（commute_mode/commute_duration/commute_desc）说明从上个节点如何到达本节点。
                7. 【跨天衔接（重要）】：多天行程时，第 N+1 天的第一个节点必须承接第 N 天最后一个节点的位置。第 N+1 天首节点的 commute_desc 必须说明如何从上一晚的下榻酒店/结束地点出发到达当天第一个目的地，严禁每天起点互不关联、跨区域跳跃。
                8. 不能生成重复景点、餐厅、酒店、小吃店。

                【用户偏好标签（务必融入行程规划）】
                ${interestTagsSection}

                【至关重要——地点真实性与城市锁定】
                9. 【核心铁律·城市锁定】当前用户要去的城市是：${city}。所有景点、餐厅、酒店、小吃店必须全部属于 ${city} 境内！严禁混入其他城市的任何地点，严禁在 address 中出现非 ${city} 的省市区名称。
                10. 【名称精确性铁律】所有地点名称必须使用高德地图上能搜到的官方全称，严格遵循以下命名规范：
                    a. 景点：必须使用景区官方注册全称，包含「风景区/景区/公园/博物馆/寺/庙/塔/故居」等后缀。
                       错误→正确：「岳麓山」→「岳麓山风景名胜区」、「橘子洲」→「橘子洲景区」、「故宫」→「故宫博物院」
                    b. 连锁品牌（餐厅/酒店）：必须包含具体分店名或路名后缀，以便高德精确检索到具体门店。
                       错误→正确：「海底捞」→「海底捞火锅(五一广场店)」、「全季酒店」→「全季酒店(目标城市步行街店)」、「星巴克」→「星巴克(太平街店)」
                    c. 非连锁本地餐厅/小吃店：使用高德/大众点评上可搜到的完整商户名，严禁使用「当地小吃店」「某网红餐厅」「附近早餐铺」等模糊指代。
                       错误→正确：「当地小吃店」→「黑色经典臭豆腐(太平街店)」、「某网红餐厅」→「炊烟时代小炒黄牛肉(步行街黄兴铜像店)」
                    d. 名称中必须保留必要的括号后缀（分店名、路名），以便高德地图精确检索和定位。
                11. 若不确定某地点官方全称，请改用该城市公认的知名地点（宁可保守用知名地标，也不要编造生僻名）。
                12. 酒店（HOTEL）须为真实存在的具体酒店全称，必须包含品牌名+分店/路名，如「亚朵酒店(${city}五一广场店)」。仅在用户明确需要酒店时才生成 HOTEL 节点。

                【地址】
                13. 每个节点必须填写 address：必须是 ${city} 境内的详细地址，格式为「区/县 + 道路 + 门牌号或地标参照物」。address 必须与 name 互补——如果 name 中已含分店名，address 需补充具体道路和门牌。示例：「天心区黄兴南路步行街388号」「岳麓区橘子洲头2号」「芙蓉区五一大道766号平和堂百货B1层」。

                【费用生成规则】
                14. 必须为每一个节点生成预估的人均消费或门票费用，填入 cost 字段（单位：元，数值型）。例如：早餐填 15，免门票景点填 0，酒店填 350。

                【JSON 输出格式】
                你必须只输出一个 JSON 对象，严格按以下结构（示例内容仅为格式说明，必须全部替换为 ${city} 的真实地点和真实数据）：
                {
                  "total_days": 整数,
                  "daily_plans": [
                    {
                      "day": 1,
                      "nodes": [
                        {
                          "type": "BREAKFAST",
                          "name": "[城市真实特色早餐店全称]",
                          "stay_time": 45,
                          "planned_arrival": "08:00",
                          "recommend_reason": "推荐理由...",
                          "address": "[城市的详细地址]",
                          "open_hours": "06:00-10:00",
                          "cost": 15.00
                        },
                        {
                          "type": "SPOT",
                          "name": "[城市景点官方全称]",
                          "stay_time": 120,
                          "planned_arrival": "09:00",
                          "recommend_reason": "推荐理由...",
                          "address": "[城市的详细地址]",
                          "open_hours": "08:00-17:00",
                          "cost": 0.00,
                          "commute_mode": "步行",
                          "commute_duration": 10,
                          "commute_desc": "从上一站交通指引"
                        }
                      ]
                    }
                  ]
                }

                【节点类型与停留说明】
                - BREAKFAST：早餐（30-60分钟）
                - LUNCH：午餐（45-90分钟）
                - DINNER：晚餐（45-90分钟）
                - SNACK：小吃/零食（15-30分钟）
                - SPOT：景点（60-300分钟，大型自然景区可达300分钟）
                - HOTEL：酒店，当天行程的终点，stay_time 设为 0

                【交通字段说明】
                - commute_mode：步行/骑行/地铁/公交/打车
                - commute_duration：耗时（分钟）
                - commute_desc：具体交通指引；跨天首节点须说明从上一晚住宿地出发的路线

                【planned_arrival 字段说明】
                - 每个节点必须输出 planned_arrival（格式 HH:mm），根据当天起始时间按顺序累加 stay_time + commute_duration 计算
                - 首日首节点不得早于 ${startTime}，末日末节点完成时间不得晚于 ${endTime}；中间天数自由发挥

                【输出约束】
                严禁输出 Markdown 围栏或任何说明文字。只输出 JSON，必须可被 JSON.parse 解析。所有字段名和字符串值使用双引号。
                """;

        String result = base.replace("${city}", city);
        result = result.replace("${startTime}", StringUtils.hasText(startTime) ? startTime.trim() : "09:00");
        result = result.replace("${endTime}", StringUtils.hasText(endTime) ? endTime.trim() : "21:00");

        // 注入用户偏好标签（按类别附加具体的规划行为指令）
        if (interestTags != null && !interestTags.isEmpty()) {
            String[] categories = {"游玩节奏", "出行成员", "消费偏好", "特殊偏好"};
            StringBuilder tagsBuilder = new StringBuilder();
            for (int i = 0; i < interestTags.size() && i < categories.length; i++) {
                String tag = interestTags.get(i).trim();
                if (!tag.isEmpty()) {
                    tagsBuilder.append("  ").append(i + 1).append(". 【").append(categories[i]).append("】").append(tag).append("\n");
                }
            }
            tagsBuilder.append("\n  请严格依据以上偏好进行规划，具体要求：\n");
            tagsBuilder.append("  - 游玩节奏直接决定每天的起止时间、景点数量和行程紧凑程度（特种兵07:00出发≥4景点，慢节奏09:30后出发≤2景点）。\n");
            tagsBuilder.append("  - 出行成员影响餐厅档次、活动强度和安全边界（亲子避开险峻徒步，老人避开长距离爬坡，情侣增加浪漫氛围点）。\n");
            tagsBuilder.append("  - 消费偏好直接决定每个节点的 cost 字段金额（穷游：早餐≤10元、正餐≤40元、优先免费景点；品质：正餐≥150元、推荐黑珍珠/米其林）。\n");
            tagsBuilder.append("  - 特殊偏好决定景点类型选择倾向（历史文化→古迹博物馆，自然风光→山水公园徒步，经典名胜→5A地标景区，冷门小众→本地人私藏秘境）。\n");
            tagsBuilder.append("  - 以上偏好必须贯穿每一天的景点选择、餐厅推荐、节奏安排和费用估算，不得只在某一天体现。");
            result = result.replace("${interestTagsSection}", tagsBuilder.toString());
        } else {
            result = result.replace("${interestTagsSection}", "（用户未指定特殊偏好，按常规旅游体验规划）");
        }
        return result;
    }

    /**
     * 从结构化字段组装 AI 用户消息（替代前端拼接的自然语言 prompt）。
     */
    public static String buildStructuredUserContent(String city, int totalDays,
                                                     String startTime, String endTime,
                                                     boolean isGenerateHotel,
                                                     List<String> interestTags) {
        StringBuilder sb = new StringBuilder();
        sb.append("去").append(city).append("玩").append(totalDays).append("天。\n");

        sb.append("时间约束：仅第一天从 ");
        sb.append(StringUtils.hasText(startTime) ? startTime.trim() : "09:00");
        sb.append(" 开始游玩，仅最后一天于 ");
        sb.append(StringUtils.hasText(endTime) ? endTime.trim() : "21:00");
        sb.append(" 结束。中间天数不受这两个时间限制，请根据用户偏好自由安排每天的起止时间并让节奏多样化。\n");

        sb.append("酒店需求：");
        sb.append(isGenerateHotel ? "需要AI推荐酒店，每天最后安排一个 HOTEL 节点。" : "不需要酒店，到晚餐或夜市结束即可，不要生成 HOTEL 节点。\n");

        // 追加用户偏好标签摘要（与 System Prompt 双重强化）
        if (interestTags != null && !interestTags.isEmpty()) {
            String[] categories = {"游玩节奏", "出行成员", "消费偏好", "特殊偏好"};
            sb.append("\n我的偏好：\n");
            for (int i = 0; i < interestTags.size() && i < categories.length; i++) {
                String tag = interestTags.get(i).trim();
                if (!tag.isEmpty()) {
                    sb.append("- ").append(categories[i]).append("：").append(tag).append("\n");
                }
            }
            sb.append("请在规划行程时严格遵循以上偏好。");
        }

        return sb.toString();
    }

    /**
     * 在用户原文后附加系统已知的起止日期，供模型对齐 daily_plans.day 与 total_days。
     */
    public static String buildTravelUserContent(String travelText, String startDate, String endDate) {
        String base = travelText != null ? travelText.trim() : "";
        if (!StringUtils.hasText(startDate) && !StringUtils.hasText(endDate)) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n【系统已确认的行程日期（必须与 JSON 中 daily_plans 的 day 序号对齐）】\n");
        if (StringUtils.hasText(startDate)) {
            sb.append("行程起始日期：").append(startDate.trim()).append("（对应第 1 天的自然日历日期）\n");
        }
        if (StringUtils.hasText(endDate)) {
            sb.append("行程结束日期：").append(endDate.trim()).append("\n");
        }
        sb.append("若上述日期与游玩天数冲突，以「起始→结束」的日历跨度与用户意图优先，并调整 total_days 与 daily_plans。");
        return sb.toString();
    }

}
