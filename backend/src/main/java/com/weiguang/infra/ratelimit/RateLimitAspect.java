package com.weiguang.infra.ratelimit;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.system.service.ConfigService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流切面（详设 §3.1）：@RateLimit 注解声明，阈值运行时从 sys_config 读取。
 * 主体标识：已登录按 uid，未登录（登录接口）按来源 IP。
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final String RATE_LIMIT_MESSAGE = "您操作得有点快，喝口水歇一下，我马上就好";

    private final RateLimitService rateLimitService;
    private final ConfigService configService;

    public RateLimitAspect(RateLimitService rateLimitService, ConfigService configService) {
        this.rateLimitService = rateLimitService;
        this.configService = configService;
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        int limit = configService.getInt(rateLimit.limitKey(), 5);
        String key = rateLimit.prefix() + subject();
        if (!rateLimitService.tryAcquire(key, limit)) {
            throw new BizException(ErrorCode.RATE_LIMITED, RATE_LIMIT_MESSAGE);
        }
        return joinPoint.proceed();
    }

    private String subject() {
        Long uid = AuthContext.getUid();
        if (uid != null) {
            return "u" + uid;
        }
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes == null ? null : attributes.getRequest();
        return "ip" + (request == null ? "unknown" : request.getRemoteAddr());
    }
}
