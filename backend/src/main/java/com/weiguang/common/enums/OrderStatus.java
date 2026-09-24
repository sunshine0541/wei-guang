package com.weiguang.common.enums;

/**
 * 求助单状态（详设 §2.6 status；终态 COMPLETED/CANCELLED/EXPIRED 不可回流）。
 */
public enum OrderStatus {

    /** 待响应 */
    PENDING,
    /** 已接单 */
    ACCEPTED,
    /** 服务中 */
    IN_SERVICE,
    /** 待确认 */
    PENDING_CONFIRM,
    /** 已完成（终态） */
    COMPLETED,
    /** 已取消（终态） */
    CANCELLED,
    /** 已超时（终态） */
    EXPIRED,
    /** 申诉中 */
    APPEALING;

    public static boolean isValid(String value) {
        for (OrderStatus status : values()) {
            if (status.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
