package com.weiguang.common.util;

/**
 * 分页参数规范化（详设 §3.1：默认 1/10，pageSize 上限 50）。
 */
public final class PageUtils {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_SIZE = 10L;
    private static final long MAX_SIZE = 50L;

    private PageUtils() {
    }

    public static long normalizePage(Long page) {
        return page == null || page < 1 ? DEFAULT_PAGE : page;
    }

    public static long normalizeSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(pageSize, MAX_SIZE);
    }
}
