package com.weiguang.common.enums;

/**
 * 时间期望（详设第 0 章确认项 7：固定单选，不开放自由文本）。
 */
public enum TimeExpect {

    /** 尽快 */
    ASAP,
    /** 今天内 */
    TODAY,
    /** 明天 */
    TOMORROW,
    /** 本周内 */
    THIS_WEEK,
    /** 不限 */
    ANY;

    public static boolean isValid(String value) {
        for (TimeExpect expect : values()) {
            if (expect.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
