package com.weiguang.infra.ratelimit;

/**
 * 限流服务（详设 §1.1：Redis 实现 + Redis 不可用时本地内存降级实现）。
 */
public interface RateLimitService {

    /**
     * 计数一次并判断是否放行（窗口 1 分钟）。
     *
     * @param key           完整键，如 rate:login:ip127.0.0.1
     * @param limitPerMinute 每分钟上限
     * @return true=放行，false=超限
     */
    boolean tryAcquire(String key, int limitPerMinute);
}
