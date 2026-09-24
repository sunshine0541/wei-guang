package com.weiguang.modules.admin.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.Result;
import com.weiguang.modules.admin.dto.AdminLoginRequest;
import com.weiguang.modules.admin.dto.AdminLoginVO;
import com.weiguang.modules.admin.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端登录（详设 §3.4；限流复用 rate.login_per_minute，键前缀 rate:admin:login:）。
 */
@RestController
@RequestMapping("/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    @RateLimit(prefix = "rate:admin:login:", limitKey = "rate.login_per_minute")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.ok(adminAuthService.login(request.getUsername(), request.getPassword()));
    }
}
