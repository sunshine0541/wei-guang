package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.admin.dto.AdminLoginVO;
import com.weiguang.modules.admin.entity.AdminUser;
import com.weiguang.modules.admin.mapper.AdminUserMapper;
import com.weiguang.modules.auth.service.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 管理端登录（详设 §3.4）：admin_user 独立表 + BCrypt + 独立 JWT（identity=admin）。
 */
@Slf4j
@Service
public class AdminAuthService {

    private static final String LOGIN_FAILED_MESSAGE = "账号或密码不正确";

    private final AdminUserMapper adminUserMapper;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminAuthService(AdminUserMapper adminUserMapper, JwtService jwtService) {
        this.adminUserMapper = adminUserMapper;
        this.jwtService = jwtService;
    }

    public AdminLoginVO login(String username, String password) {
        AdminUser admin = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (admin == null
                || !"NORMAL".equals(admin.getStatus())
                || !passwordEncoder.matches(password, admin.getPasswordHash())) {
            log.warn("管理端登录失败 username={}", username);
            throw new BizException(ErrorCode.UNAUTHORIZED, LOGIN_FAILED_MESSAGE);
        }

        AdminUser update = new AdminUser();
        update.setId(admin.getId());
        update.setLastLoginAt(LocalDateTime.now());
        adminUserMapper.updateById(update);

        return new AdminLoginVO(jwtService.issue(admin.getId(), JwtService.IDENTITY_ADMIN), admin.getUsername());
    }
}
