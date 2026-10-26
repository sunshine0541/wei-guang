package com.weiguang.common.enums;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 求助分类（4C 保底时长用）：把受控 tags 映射到 8 个业务分类，按分类读取隐藏保底分钟数。
 * 多标签时取映射到的多个分类，风险判定按其中最低的保底值计算（减少误报）。
 */
public enum OrderCategory {

    PAOTUI("跑腿", "risk.min_minutes.paotui", 5, Set.of("跑腿")),
    REPAIR("小修小补", "risk.min_minutes.repair", 10, Set.of("维修")),
    HOUSEWORK("家务搭把手", "risk.min_minutes.housework", 8, Set.of("家务")),
    ACCOMPANY("陪伴同行", "risk.min_minutes.accompany", 10, Set.of("陪伴", "散步", "聊天")),
    TEACH("教学", "risk.min_minutes.teach", 5, Set.of("教学")),
    ONLINE("网上办事", "risk.min_minutes.online", 5, Set.of("网上办事")),
    PLANTS("花草宠物", "risk.min_minutes.plants", 5, Set.of("宠物花草")),
    OTHER("其他小事", "risk.min_minutes.other", 3, Set.of("其他"));

    private final String label;
    private final String configKey;
    private final int defaultMinMinutes;
    private final Set<String> tags;

    OrderCategory(String label, String configKey, int defaultMinMinutes, Set<String> tags) {
        this.label = label;
        this.configKey = configKey;
        this.defaultMinMinutes = defaultMinMinutes;
        this.tags = tags;
    }

    public String label() {
        return label;
    }

    public String configKey() {
        return configKey;
    }

    public int defaultMinMinutes() {
        return defaultMinMinutes;
    }

    /**
     * 按 tags 顺序映射分类；没有任何匹配时落到「其他小事」。
     */
    public static List<OrderCategory> ofTags(List<String> tags) {
        LinkedHashSet<OrderCategory> result = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                for (OrderCategory category : values()) {
                    if (category.tags.contains(tag)) {
                        result.add(category);
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result.add(OTHER);
        }
        return List.copyOf(result);
    }
}
