package com.weiguang.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流声明（详设 §3.1）：阈值从 sys_config 运行时读取，改配置即生效。
 * 键前缀固定四类：rate:login: / rate:publish: / rate:chat: / rate:admin:login:
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Redis 键前缀，如 rate:login: */
    String prefix();

    /** 阈值所在的 sys_config key，如 rate.login_per_minute */
    String limitKey();
}
