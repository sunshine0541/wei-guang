package com.weiguang.config;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Web 层配置：接口时间返回格式统一为 yyyy-MM-dd HH:mm:ss（详设 §2.1 通用约定）。
 * 静态资源映射 /files/** 在 T04A 落地时补充到本类。
 */
@Configuration
public class WebConfig {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            builder.serializerByType(LocalDateTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer(DATE_TIME_FORMATTER));
            builder.serializerByType(LocalDate.class,
                    new com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer(DATE_FORMATTER));
            builder.deserializerByType(LocalDateTime.class,
                    new com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer(DATE_TIME_FORMATTER));
        };
    }
}
