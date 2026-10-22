package com.weiguang.common.constants;

import java.util.List;

/**
 * 快捷短语（详设 §8.4：seed 即 5 条，固定常量，无 DB）。
 * B：按角色区分——求助者与接单者各 5 条。
 */
public final class QuickPhraseConstants {

    /** 求助者常用 */
    public static final List<String> REQUESTER_PHRASES = List.of(
            "好的", "麻烦你了", "我在家门口", "稍等一下", "谢谢你");

    /** 接单者常用 */
    public static final List<String> HELPER_PHRASES = List.of(
            "我来啦", "现在方便吗", "还需要我做什么", "不急，您慢慢来", "有问题随时说");

    /** 兼容默认（未指定角色时按求助者返回） */
    public static final List<String> QUICK_PHRASES = REQUESTER_PHRASES;

    private QuickPhraseConstants() {
    }

    public static List<String> of(String role) {
        return "HELPER".equalsIgnoreCase(role) ? HELPER_PHRASES : REQUESTER_PHRASES;
    }
}
