package com.weiguang.common.enums;

/**
 * 求助类型（详设 §2.6 order_type）。
 */
public enum OrderType {

    /** 上门 */
    HOME_VISIT("urgency.base_home"),
    /** 陪同外出 */
    ACCOMPANY("urgency.base_accompany"),
    /** 远程 */
    REMOTE("urgency.base_remote");

    private final String urgencyBaseConfigKey;

    OrderType(String urgencyBaseConfigKey) {
        this.urgencyBaseConfigKey = urgencyBaseConfigKey;
    }

    /** 该类型的紧急度基准分对应的 sys_config key */
    public String urgencyBaseConfigKey() {
        return urgencyBaseConfigKey;
    }

    /** 是否需要填写地址（上门/陪同必填，远程必须为 NULL） */
    public boolean requiresAddress() {
        return this != REMOTE;
    }

    /** 是否上门类（上门类才有门牌，确认开始后对接单者解锁） */
    public boolean hasDetailAddress() {
        return this == HOME_VISIT;
    }

    public static boolean isValid(String value) {
        for (OrderType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}
