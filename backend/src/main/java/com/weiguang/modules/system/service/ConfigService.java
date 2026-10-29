package com.weiguang.modules.system.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.system.entity.SysConfig;
import com.weiguang.modules.system.mapper.SysConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置服务（详设 §2.22 生效机制）：启动全量加载进内存缓存 + 管理端保存即刷新 + J9 定时刷新兜底。
 * 业务代码只从本类读取配置，禁止硬编码。
 */
@Slf4j
@Service
public class ConfigService implements ApplicationRunner {

    /** J9 刷新间隔取自 sys_config（秒） */
    private static final String REFRESH_INTERVAL_KEY = "sys.config_refresh_seconds";
    /** 写死禁改配置（详设 §7：ai.thinking 固定 false，管理端不可改） */
    private static final Set<String> IMMUTABLE_KEYS = Set.of("ai.thinking");
    /** 数字范围 value_range（如 "50-100"、"-100-0"） */
    private static final Pattern RANGE_PATTERN = Pattern.compile("^(-?\\d+)-(-?\\d+)$");
    /** 长度类配置的列宽硬上限：value_range 可以放宽，但落库前必须小于等于对应列宽 */
    private static final Map<String, Integer> COLUMN_MAX_LENGTH = Map.of(
            "help.title_max_length", 20,
            "content.desc_max_length", 200,
            "content.chat_text_max_length", 500,
            "ai.letter_max_length", 500,
            "community.post_max_length", 1000,
            "community.comment_max_length", 200
    );

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

    /** 管理端修改配置（详设 §3.4）：按 value_range 校验 → 落库 → 保存即刷新缓存 */
    @Transactional
    public void update(Long adminId, String key, String value) {
        String trimmed = value == null ? null : value.trim();
        if (key == null || key.isBlank() || trimmed == null || trimmed.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        SysConfig config = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                .eq(SysConfig::getConfigKey, key));
        if (config == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (IMMUTABLE_KEYS.contains(key)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        validateValue(config.getValueRange(), trimmed);
        validateColumnMax(key, trimmed);
        sysConfigMapper.update(null, Wrappers.<SysConfig>lambdaUpdate()
                .eq(SysConfig::getConfigKey, key)
                .set(SysConfig::getConfigValue, trimmed)
                .set(SysConfig::getUpdatedBy, String.valueOf(adminId)));
        refresh();
        log.info("配置修改生效：{} -> {}（admin={}）", key, trimmed, adminId);
    }

    /**
     * value_range 校验：数字范围（"50-100"/"-100-0"）校验整数与区间；枚举（"real/mock"，
     * 允许中文括注后缀如 "true/false（…）"）校验取值；描述性范围（"合法路径"等）放行。
     */
    private void validateValue(String valueRange, String value) {
        String range = valueRange == null ? "" : valueRange.replace('\u2013', '-').trim();
        int noteIndex = range.indexOf('（');
        if (noteIndex >= 0) {
            range = range.substring(0, noteIndex).trim();
        }
        if (range.contains("/")) {
            boolean matched = Arrays.stream(range.split("/"))
                    .map(String::trim)
                    .anyMatch(option -> option.equals(value));
            if (!matched) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            return;
        }
        Matcher matcher = RANGE_PATTERN.matcher(range);
        if (matcher.matches()) {
            long min = Long.parseLong(matcher.group(1));
            long max = Long.parseLong(matcher.group(2));
            long number;
            try {
                number = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            if (number < min || number > max) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        }
    }

    /** 长度类配置还要受对应数据库列宽约束，避免“合法范围”内却写库溢出 */
    private void validateColumnMax(String key, String value) {
        Integer max = COLUMN_MAX_LENGTH.get(key);
        if (max == null) {
            return;
        }
        try {
            if (Long.parseLong(value) > max) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.PARAM_INVALID);
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
