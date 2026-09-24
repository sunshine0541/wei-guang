package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.admin.entity.AdminUser;
import com.weiguang.modules.admin.mapper.AdminUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 管理端初始账号（详设 §8.2）：用户名/初始密码取环境变量 ADMIN_SEED_USERNAME / ADMIN_SEED_PASSWORD，
 * BCrypt 入库；已存在则跳过（不覆盖已改过的密码）。
 */
@Slf4j
@Component
public class AdminSeedInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String username;
    private final String password;

    public AdminSeedInitializer(AdminUserMapper adminUserMapper,
                                @Value("${ADMIN_SEED_USERNAME:admin}") String username,
                                @Value("${ADMIN_SEED_PASSWORD:}") String password) {
        this.adminUserMapper = adminUserMapper;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password == null || password.isBlank()) {
            log.warn("ADMIN_SEED_PASSWORD 未配置，跳过管理端初始账号初始化");
            return;
        }
        AdminUser existing = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (existing != null) {
            log.info("管理端账号 {} 已存在，跳过初始化", username);
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setStatus("NORMAL");
        adminUserMapper.insert(admin);
        log.info("已初始化管理端账号 {}", username);
    }
}
