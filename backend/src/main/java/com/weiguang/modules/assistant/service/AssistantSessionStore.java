package com.weiguang.modules.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.infra.ratelimit.RedisHealthProbe;
import com.weiguang.modules.assistant.dto.AssistantSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 助手会话存储：Redis 2 小时 TTL；Redis 不可用时降级为本地内存（单实例演示足够）。
 * 会话只保存发布草稿与确认令牌，不作为业务真源。
 */
@Slf4j
@Service
public class AssistantSessionStore {

    private static final Duration TTL = Duration.ofHours(2);

    private final StringRedisTemplate redisTemplate;
    private final RedisHealthProbe redisHealthProbe;
    private final ObjectMapper objectMapper;
    private final Map<String, LocalEntry> local = new ConcurrentHashMap<>();

    public AssistantSessionStore(StringRedisTemplate redisTemplate,
                                 RedisHealthProbe redisHealthProbe,
                                 ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.redisHealthProbe = redisHealthProbe;
        this.objectMapper = objectMapper;
    }

    public AssistantSession get(String key) {
        if (redisHealthProbe.isUsable()) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    return objectMapper.readValue(json, AssistantSession.class);
                }
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
        LocalEntry entry = local.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expireAt()) {
            local.remove(key);
            return null;
        }
        return entry.session();
    }

    public void put(String key, AssistantSession session) {
        session.setUpdatedAt(System.currentTimeMillis());
        local.put(key, new LocalEntry(session, System.currentTimeMillis() + TTL.toMillis()));
        if (redisHealthProbe.isUsable()) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(session), TTL);
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
    }

    /**
     * 原子消费确认令牌：只有第一个携带正确 token 的调用能拿到会话，
     * 并发两次 confirm 时第二次直接失败，避免重复建单。
     */
    public AssistantSession consume(String key, String token) {
        AssistantSession session = null;
        if (redisHealthProbe.isUsable()) {
            try {
                String json = redisTemplate.opsForValue().getAndDelete(key);
                if (json != null) {
                    session = objectMapper.readValue(json, AssistantSession.class);
                }
            } catch (Exception e) {
                // 消费失败时尽力删除 Redis 键，避免恢复后同一会话被二次消费
                try {
                    redisTemplate.delete(key);
                } catch (Exception ignored) {
                    // Redis 不可用时交给本地降级路径
                }
                redisHealthProbe.markUnusable(e);
            }
        }
        if (session == null) {
            LocalEntry entry = local.remove(key);
            if (entry != null && System.currentTimeMillis() <= entry.expireAt()) {
                session = entry.session();
            }
        } else {
            local.remove(key);
        }
        if (session == null) {
            return null;
        }
        if (token == null || !token.equals(session.getConfirmToken())) {
            // 令牌不匹配：把会话放回，避免误删用户草稿
            put(key, session);
            return null;
        }
        return session;
    }

    public void remove(String key) {
        local.remove(key);
        if (redisHealthProbe.isUsable()) {
            try {
                redisTemplate.delete(key);
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
    }

    private record LocalEntry(AssistantSession session, long expireAt) {
    }
}
