package com.weiguang.common.constants;

import java.util.List;

/**
 * 快捷短语（详设 §8.4：seed 即 5 条，固定常量，无 DB）。
 */
public final class QuickPhraseConstants {

    public static final List<String> QUICK_PHRASES = List.of(
            "好的", "麻烦你了", "我在家门口", "稍等一下", "谢谢你");

    private QuickPhraseConstants() {
    }
}
