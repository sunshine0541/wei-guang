package com.weiguang.modules.auth.service;

import com.weiguang.modules.light.service.LightEventRecorder;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 建号器（A1 与账密注册共用，D31）：建号 + 建空画像 + 光语初始事件（同事务）。
 */
@Slf4j
@Service
public class UserRegistrar {

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ConfigService configService;
    private final LightEventRecorder lightEventRecorder;

    public UserRegistrar(UserMapper userMapper,
                         UserProfileMapper userProfileMapper,
                         ConfigService configService,
                         LightEventRecorder lightEventRecorder) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.configService = configService;
        this.lightEventRecorder = lightEventRecorder;
    }

    /** 建号（openid 与 username/passwordHash 按注册方式传入，未用者传 null） */
    @Transactional
    public User register(String openid, String username, String passwordHash) {
        User user = new User();
        user.setOpenid(openid);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setUiMode("NORMAL");
        user.setLightState("UNLIT");
        user.setCreditScore(configService.getInt("credit.init", 100));
        userMapper.insert(user);
        // 光语映射（详设 §4.2）：建号 → UNLIT + 事件
        lightEventRecorder.record(LightEventRecorder.TYPE_STATE_CHANGE, user.getId(), null, "UNLIT", null, null);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        userProfileMapper.insert(profile);
        log.info("新用户建号完成 uid={}（username={}）", user.getId(), username);
        return user;
    }
}