package com.weiguang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

/**
 * 微光——社区互助服务系统后端启动类。
 * JVM 时区固定 Asia/Shanghai（详设 §2.1 通用约定）。
 */
@SpringBootApplication
public class WeiguangApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        SpringApplication.run(WeiguangApplication.class, args);
    }
}
