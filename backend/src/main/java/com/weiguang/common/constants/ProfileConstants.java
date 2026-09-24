package com.weiguang.common.constants;

/**
 * 画像与地址簿的长度/数量常量（详设第 0 章确认项 8：纯展示限制用常量，不入 sys_config）。
 */
public final class ProfileConstants {

    /** 昵称上限（字） */
    public static final int NICKNAME_MAX_LENGTH = 20;
    /** 个性签名上限（字） */
    public static final int SIGNATURE_MAX_LENGTH = 50;
    /** 需要留意的小习惯上限（字） */
    public static final int CARE_TIP_MAX_LENGTH = 100;
    /** 地址簿单条文本上限（字） */
    public static final int ADDRESS_TEXT_MAX_LENGTH = 100;
    /** 地址簿标签上限（字） */
    public static final int ADDRESS_LABEL_MAX_LENGTH = 20;
    /** 地址簿条数上限 */
    public static final int ADDRESS_MAX_COUNT = 10;
    /** 年龄下限/上限（自主申报，边界校验用） */
    public static final int AGE_MIN = 0;
    public static final int AGE_MAX = 150;
    /** 注销后昵称（U6 匿名化，J10 留存清理同用） */
    public static final String CANCELLED_NICKNAME = "已注销用户";

    private ProfileConstants() {
    }
}
