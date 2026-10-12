package com.weiguang.config;

import com.weiguang.infra.interceptor.AdminAuthInterceptor;
import com.weiguang.infra.interceptor.UserAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * 拦截器注册与白名单（详设 §3.1：两套拦截器隔离）。
 * 白名单清单写死在本类，不得散落到别处。
 */
@Configuration
public class SecurityConfig implements WebMvcConfigurer {

    /** 用户端白名单（免登录） */
    public static final String[] USER_WHITELIST = {
            "/api/auth/wx-login",
            "/api/auth/register",
            "/api/auth/login"
    };

    /** 管理端白名单（免登录） */
    public static final String[] ADMIN_WHITELIST = {
            "/admin/auth/login"
    };

    /** 两端共用放行：监控端点、静态文件、接口文档 */
    public static final String[] COMMON_WHITELIST = {
            "/actuator/**",
            "/files/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    private final UserAuthInterceptor userAuthInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;

    public SecurityConfig(UserAuthInterceptor userAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
        this.userAuthInterceptor = userAuthInterceptor;
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userAuthInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(merge(USER_WHITELIST, COMMON_WHITELIST));

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/admin/**")
                .excludePathPatterns(merge(ADMIN_WHITELIST, COMMON_WHITELIST));
    }

    private String[] merge(String[] first, String[] second) {
        String[] merged = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, merged, first.length, second.length);
        return merged;
    }
}
