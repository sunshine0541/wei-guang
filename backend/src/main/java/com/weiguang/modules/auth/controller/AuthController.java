package com.weiguang.modules.auth.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.auth.dto.WxLoginRequest;
import com.weiguang.modules.auth.dto.WxLoginVO;
import com.weiguang.modules.auth.service.WxAuthService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 鉴权接口（详设 §3.2 A1/A2）。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final WxAuthService wxAuthService;

    public AuthController(WxAuthService wxAuthService) {
        this.wxAuthService = wxAuthService;
    }

    /** A1 微信静默登录（限流 5 次/分） */
    @PostMapping("/wx-login")
    @RateLimit(prefix = "rate:login:", limitKey = "rate.login_per_minute")
    public Result<WxLoginVO> wxLogin(@Valid @RequestBody WxLoginRequest request) {
        return Result.ok(wxAuthService.wxLogin(request.getCode()));
    }

    /** A2 退出登录：无状态 JWT，仅前端清 token，服务端记录日志 */
    @PostMapping("/logout")
    public Result<Void> logout() {
        log.info("用户退出登录 uid={}", AuthContext.getUid());
        return Result.ok();
    }
}
