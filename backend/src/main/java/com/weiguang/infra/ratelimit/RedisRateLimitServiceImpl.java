package com.weiguang.infra.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 限流 Redis 实现（详设 §3.1）：INCR + TTL 60s 固定窗口。
 * Redis 只存计数与 TTL，不存任何业务真源。
 */
@Component
public class RedisRateLimitServiceImpl implements RateLimitService {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryAcquire(String key, int limitPerMinute) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count == null) {
            return true;
        }
        if (count == 1L) {
            redisTemplate.expire(key, WINDOW);
        }
        return count <= limitPerMinute;
    }
}
