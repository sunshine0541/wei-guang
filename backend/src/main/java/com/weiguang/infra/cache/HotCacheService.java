package com.weiguang.infra.cache;

import com.weiguang.infra.ratelimit.RedisHealthProbe;
import com.weiguang.modules.system.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 热点缓存（详设 §1.1 白名单用途③）：仅缓存读多写少数据（求助模板表等），TTL 取 cache.hot_ttl_seconds。
 * Redis 不可用或 redis.enabled=false 时自动降级为本地内存实现；禁止缓存求助列表、状态机、账本等业务真源。
 */
@Slf4j
@Service
public class HotCacheService {

    private final StringRedisTemplate redisTemplate;
    private final RedisHealthProbe redisHealthProbe;
    private final ConfigService configService;

    private final Map<String, LocalEntry> localCache = new ConcurrentHashMap<>();

    public HotCacheService(StringRedisTemplate redisTemplate,
                           RedisHealthProbe redisHealthProbe,
                           ConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.redisHealthProbe = redisHealthProbe;
        this.configService = configService;
    }

    /** 读缓存，未命中返回 null */
    public String get(String key) {
        if (redisHealthProbe.isUsable()) {
            try {
                return redisTemplate.opsForValue().get(key);
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
        LocalEntry entry = localCache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt() >= ttlMillis()) {
            localCache.remove(key);
            return null;
        }
        return entry.value();
    }

    public void put(String key, String value) {
        if (redisHealthProbe.isUsable()) {
            try {
                redisTemplate.opsForValue().set(key, value, Duration.ofMillis(ttlMillis()));
                return;
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
        localCache.put(key, new LocalEntry(value, System.currentTimeMillis()));
    }

    public void evict(String key) {
        localCache.remove(key);
        if (redisHealthProbe.isUsable()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
    }

    private long ttlMillis() {
        return configService.getInt("cache.hot_ttl_seconds", 60) * 1000L;
    }

    private record LocalEntry(String value, long createdAt) {
    }
}
