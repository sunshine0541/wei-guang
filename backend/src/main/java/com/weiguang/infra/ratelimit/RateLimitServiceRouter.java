package com.weiguang.infra.ratelimit;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 限流实现路由：Redis 可用走 Redis，否则走本地内存（详设 §1.1 自动降级）。
 */
@Primary
@Component
public class RateLimitServiceRouter implements RateLimitService {

    private final RedisRateLimitServiceImpl redisImpl;
    private final LocalRateLimitServiceImpl localImpl;
    private final RedisHealthProbe redisHealthProbe;

    public RateLimitServiceRouter(RedisRateLimitServiceImpl redisImpl,
                                  LocalRateLimitServiceImpl localImpl,
                                  RedisHealthProbe redisHealthProbe) {
        this.redisImpl = redisImpl;
        this.localImpl = localImpl;
        this.redisHealthProbe = redisHealthProbe;
    }

    @Override
    public boolean tryAcquire(String key, int limitPerMinute) {
        if (redisHealthProbe.isUsable()) {
            try {
                return redisImpl.tryAcquire(key, limitPerMinute);
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
        return localImpl.tryAcquire(key, limitPerMinute);
    }
}
