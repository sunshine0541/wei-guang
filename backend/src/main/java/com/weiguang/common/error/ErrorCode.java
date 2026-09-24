package com.weiguang.common.error;

import java.util.Map;

/**
 * 错误码常量（照抄详设 §3.1 错误码清单，不得自行发明）。
 */
public final class ErrorCode {

    /** 成功 */
    public static final int SUCCESS = 0;
    /** 参数校验失败 */
    public static final int PARAM_INVALID = 10001;
    /** 内容超长 */
    public static final int CONTENT_TOO_LONG = 10002;
    /** 内容未通过检查（发布内容命中本地敏感词，温柔拦截） */
    public static final int CONTENT_BLOCKED = 10003;
    /** 未登录或凭证无效 */
    public static final int UNAUTHORIZED = 20001;
    /** 凭证已过期 */
    public static final int TOKEN_EXPIRED = 20002;
    /** 无权访问该资源 */
    public static final int FORBIDDEN = 20003;
    /** 账号已被限制 */
    public static final int ACCOUNT_RESTRICTED = 20004;
    /** 接单冷却中 */
    public static final int ACCEPT_COOLDOWN = 30002;
    /** 待响应求助已达上限 */
    public static final int PENDING_LIMIT = 30003;
    /** 需先补填年龄 */
    public static final int AGE_REQUIRED = 30004;
    /** 操作太频繁 */
    public static final int RATE_LIMITED = 30005;
    /** 微光值不足，仅可接远程类求助 */
    public static final int CREDIT_INSUFFICIENT = 30006;
    /** 地址簿已达上限 */
    public static final int ADDRESS_LIMIT = 30007;
    /** 手慢了，这束微光已被接走 */
    public static final int ACCEPT_CONFLICT = 40001;
    /** 当前状态不允许该操作 */
    public static final int STATE_NOT_ALLOWED = 40002;
    /** 请勿重复提交 */
    public static final int DUPLICATE_SUBMIT = 40003;
    /** 系统繁忙，请稍后再试 */
    public static final int SYSTEM_ERROR = 50001;
    /** 依赖服务暂时不可用 */
    public static final int DEPENDENCY_UNAVAILABLE = 50002;

    private static final Map<Integer, String> MESSAGES = Map.ofEntries(
            Map.entry(SUCCESS, "成功"),
            Map.entry(PARAM_INVALID, "参数校验失败"),
            Map.entry(CONTENT_TOO_LONG, "内容超长"),
            Map.entry(CONTENT_BLOCKED, "内容未通过检查"),
            Map.entry(UNAUTHORIZED, "未登录或凭证无效"),
            Map.entry(TOKEN_EXPIRED, "凭证已过期"),
            Map.entry(FORBIDDEN, "无权访问该资源"),
            Map.entry(ACCOUNT_RESTRICTED, "账号已被限制"),
            Map.entry(ACCEPT_COOLDOWN, "接单冷却中"),
            Map.entry(PENDING_LIMIT, "待响应求助已达上限"),
            Map.entry(AGE_REQUIRED, "需先补填年龄"),
            Map.entry(RATE_LIMITED, "操作太频繁"),
            Map.entry(CREDIT_INSUFFICIENT, "微光值不足，仅可接远程类求助"),
            Map.entry(ADDRESS_LIMIT, "地址簿已达上限"),
            Map.entry(ACCEPT_CONFLICT, "手慢了，这束微光已被接走"),
            Map.entry(STATE_NOT_ALLOWED, "当前状态不允许该操作"),
            Map.entry(DUPLICATE_SUBMIT, "请勿重复提交"),
            Map.entry(SYSTEM_ERROR, "系统繁忙，请稍后再试"),
            Map.entry(DEPENDENCY_UNAVAILABLE, "依赖服务暂时不可用")
    );

    private ErrorCode() {
    }

    public static String messageOf(int code) {
        return MESSAGES.getOrDefault(code, "系统繁忙，请稍后再试");
    }
}
