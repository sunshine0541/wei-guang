package com.weiguang.modules.system.service;

import com.weiguang.modules.system.entity.SysConfig;
import com.weiguang.modules.system.mapper.SysConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 配置服务（详设 §2.22 生效机制）：启动全量加载进内存缓存 + 管理端保存即刷新 + J9 定时刷新兜底。
 * 业务代码只从本类读取配置，禁止硬编码。
 */
@Slf4j
@Service
public class ConfigService implements ApplicationRunner {

    /** J9 刷新间隔取自 sys_config（秒） */
    private static final String REFRESH_INTERVAL_KEY = "sys.config_refresh_seconds";

    private final SysConfigMapper sysConfigMapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private volatile long lastRefreshAt = 0L;

    public ConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
        // 构造即计时，避免 J9 在启动瞬间与 ApplicationRunner 重复加载一次
        this.lastRefreshAt = System.currentTimeMillis();
    }

    @Override
    public void run(ApplicationArguments args) {
        refresh();
    }

    /** 全量重载配置：启动、管理端保存后调用、J9 定时兜底均走此入口 */
    public void refresh() {
        List<SysConfig> list = sysConfigMapper.selectList(null);
        Map<String, String> fresh = new HashMap<>(list.size() * 2);
        List<String> changed = new ArrayList<>();
        for (SysConfig config : list) {
            String previous = cache.get(config.getConfigKey());
            if (previous != null && !previous.equals(config.getConfigValue())) {
                changed.add(config.getConfigKey() + " " + previous + " -> " + config.getConfigValue());
            }
            fresh.put(config.getConfigKey(), config.getConfigValue());
        }
        cache.clear();
        cache.putAll(fresh);
        lastRefreshAt = System.currentTimeMillis();
        log.info("配置加载 {} 项", cache.size());
        if (!changed.isEmpty()) {
            log.info("配置变更生效：{}", String.join("; ", changed));
        }
    }

    /** J9 配置刷新：按 sys_config 中的间隔兜底重载 */
    @Scheduled(fixedDelay = 10_000)
    public void scheduledRefresh() {
        long intervalMs = getInt(REFRESH_INTERVAL_KEY, 60) * 1000L;
        if (System.currentTimeMillis() - lastRefreshAt < intervalMs) {
            return;
        }
        refresh();
    }

    public String get(String key) {
        return cache.get(key);
    }

    public String get(String key, String defaultValue) {
        String value = cache.get(key);
        return value == null ? defaultValue : value;
    }

    public int getInt(String key, int defaultValue) {
        String value = cache.get(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            log.warn("配置 {} 值不是整数：{}，使用默认值 {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBool(String key, boolean defaultValue) {
        String value = cache.get(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.trim());
    }
}
