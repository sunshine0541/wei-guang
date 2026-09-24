package com.weiguang.infra.interceptor;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;

/**
 * 当前请求身份上下文（由拦截器写入，请求结束后清理）。
 */
public final class AuthContext {

    private static final ThreadLocal<Long> UID = new ThreadLocal<>();
    private static final ThreadLocal<Long> ADMIN_ID = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setUid(Long uid) {
        UID.set(uid);
    }

    public static Long getUid() {
        return UID.get();
    }

    /** 取当前登录用户 id，未登录直接抛 20001 */
    public static Long requireUid() {
        Long uid = UID.get();
        if (uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return uid;
    }

    public static void setAdminId(Long adminId) {
        ADMIN_ID.set(adminId);
    }

    public static Long getAdminId() {
        return ADMIN_ID.get();
    }

    public static void clear() {
        UID.remove();
        ADMIN_ID.remove();
    }
}
