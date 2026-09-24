package com.weiguang.config;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Actuator 配置：端点暴露范围（health/info/metrics，敏感端点排除）在 application.yml 中声明；
 * 本类只提供 /actuator/info 的应用信息明细。
 */
@Configuration
public class ActuatorConfig {

    @Bean
    public InfoContributor weiguangInfoContributor() {
        return builder -> builder
                .withDetail("name", "微光——社区互助服务系统")
                .withDetail("version", "1.0.0");
    }
}
