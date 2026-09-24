package com.weiguang.common.enums;

/**
 * 求助来源（详设 §2.6 source）。
 */
public enum OrderSource {

    /** 模板发布 */
    TEMPLATE,
    /** 自由描述 */
    FREE,
    /** 再来一次 */
    REPEAT;

    public static boolean isValid(String value) {
        for (OrderSource source : values()) {
            if (source.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
