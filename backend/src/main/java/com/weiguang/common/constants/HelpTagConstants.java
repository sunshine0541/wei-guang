package com.weiguang.common.constants;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 受控标签词表与"喜欢的事 → 帮助方向"映射（详设 §8.4、第 0 章确认项 10）。
 * 本地映射，不调 AI；用户确认后生效。
 */
public final class HelpTagConstants {

    /** 受控标签词表（10 项），help_directions 与求助单 tags 均取此表子集 */
    public static final List<String> TAGS = List.of(
            "跑腿代买", "维修", "家务", "陪伴", "散步", "手机教学", "网上办事", "宠物花草", "聊天", "其他");

    /** 喜欢做的事选项（8 项，确认项 10） */
    public static final List<String> HOBBY_OPTIONS = List.of(
            "聊天", "养花", "下棋", "散步", "做饭", "看书", "手机使用", "手工维修");

    /** 喜欢的事 → 帮助方向映射（确认项 10 写死） */
    private static final Map<String, List<String>> HOBBY_TO_DIRECTIONS = Map.of(
            "聊天", List.of("聊天", "陪伴"),
            "养花", List.of("宠物花草"),
            "下棋", List.of("陪伴", "聊天"),
            "散步", List.of("散步", "陪伴"),
            "做饭", List.of("家务"),
            "看书", List.of("聊天"),
            "手机使用", List.of("手机教学", "网上办事"),
            "手工维修", List.of("维修"));

    private HelpTagConstants() {
    }

    public static boolean isTag(String value) {
        return TAGS.contains(value);
    }

    public static boolean isHobby(String value) {
        return HOBBY_OPTIONS.contains(value);
    }

    /** 按选项固定顺序去重汇总帮助方向 */
    public static List<String> directionsOf(Collection<String> hobbies) {
        Set<String> directions = new LinkedHashSet<>();
        if (hobbies != null) {
            for (String hobby : HOBBY_OPTIONS) {
                if (hobbies.contains(hobby)) {
                    directions.addAll(HOBBY_TO_DIRECTIONS.getOrDefault(hobby, List.of()));
                }
            }
        }
        return List.copyOf(directions);
    }
}
