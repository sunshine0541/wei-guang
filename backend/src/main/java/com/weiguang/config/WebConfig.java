package com.weiguang.config;

import com.weiguang.infra.storage.LocalStorageServiceImpl;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Web 层配置：接口时间返回格式统一为 yyyy-MM-dd HH:mm:ss（详设 §2.1 通用约定）；
 * 静态资源映射 /files/** → storage.local_path（详设 §1.5 图片上传链路）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LocalStorageServiceImpl localStorageService;

    public WebConfig(LocalStorageServiceImpl localStorageService) {
        this.localStorageService = localStorageService;
    }

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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(localStorageService.rootPath()).toAbsolutePath().normalize();
        String location = root.toUri().toString();
        if (!location.endsWith("/")) {
            location = location + "/";
        }
        registry.addResourceHandler(LocalStorageServiceImpl.URL_PREFIX + "**")
                .addResourceLocations(location);
    }
}
