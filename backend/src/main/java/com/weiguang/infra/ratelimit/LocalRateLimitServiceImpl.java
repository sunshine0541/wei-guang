package com.weiguang.infra.ratelimit;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流本地降级实现（详设 §1.1）：ConcurrentHashMap + TTL，Redis 不可用或 redis.enabled=false 时启用。
 */
@Component
public class LocalRateLimitServiceImpl implements RateLimitService {

    private static final long WINDOW_MS = 60_000L;
    private static final int PRUNE_THRESHOLD = 1024;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limitPerMinute) {
        long now = System.currentTimeMillis();
        if (windows.size() > PRUNE_THRESHOLD) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startMs() >= WINDOW_MS);
        }
        Window window = windows.compute(key, (k, current) ->
                (current == null || now - current.startMs() >= WINDOW_MS)
                        ? new Window(now, 1)
                        : new Window(current.startMs(), current.count() + 1));
        return window.count() <= limitPerMinute;
    }

    private record Window(long startMs, int count) {
    }
}
