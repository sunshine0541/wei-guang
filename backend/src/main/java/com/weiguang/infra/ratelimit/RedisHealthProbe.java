package com.weiguang.infra.ratelimit;

import com.weiguang.modules.system.service.ConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redis 启动探测（详设 §1.1）：redis.enabled=false 直接走降级；探测失败只打印 WARN，不阻止启动。
 */
@Slf4j
@Component
public class RedisHealthProbe implements ApplicationRunner {

    private final StringRedisTemplate redisTemplate;
    private final ConfigService configService;

    private volatile boolean usable = false;

    public RedisHealthProbe(StringRedisTemplate redisTemplate, ConfigService configService) {
        this.redisTemplate = redisTemplate;
        this.configService = configService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!configService.getBool("redis.enabled", true)) {
            log.warn("redis.enabled=false，限流与热点缓存全部走本地降级实现");
            return;
        }
        if (ping()) {
            usable = true;
            log.info("Redis 探测成功，限流与热点缓存使用 Redis 实现");
        } else {
            log.warn("Redis 探测失败，自动降级为本地内存实现");
        }
    }

    /** 运行期每 30s 复探一次：Redis 恢复后自动切回，无需重启 */
    @Scheduled(fixedDelay = 30_000)
    public void reprobe() {
        if (usable || !configService.getBool("redis.enabled", true)) {
            return;
        }
        if (ping()) {
            usable = true;
            log.info("Redis 已恢复，限流与热点缓存切回 Redis 实现");
        }
    }

    private boolean ping() {
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try {
                connection.ping();
            } finally {
                connection.close();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isUsable() {
        return usable;
    }

    /** 运行期 Redis 异常时降级（只告警一次） */
    public void markUnusable(Exception e) {
        if (usable) {
            usable = false;
            log.warn("Redis 运行期异常，降级为本地内存实现：{}", e.getMessage());
        }
    }
}
