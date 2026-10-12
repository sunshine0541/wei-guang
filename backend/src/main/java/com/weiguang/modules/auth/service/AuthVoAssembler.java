package com.weiguang.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.auth.dto.WxLoginVO;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;

/**
 * 登录出参统一组装（A1 / 账密注册 / 账密登录共用，D31）：
 * token + isNewUser + needPrivacyAgree + creditScore + profile + config。
 */
@Service
public class AuthVoAssembler {

    private final UserProfileMapper userProfileMapper;
    private final JwtService jwtService;
    private final ConfigService configService;

    public AuthVoAssembler(UserProfileMapper userProfileMapper,
                           JwtService jwtService,
                           ConfigService configService) {
        this.userProfileMapper = userProfileMapper;
        this.jwtService = jwtService;
        this.configService = configService;
    }

    public WxLoginVO assemble(User user, boolean isNewUser) {
        UserProfile profile = userProfileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, user.getId()));

        WxLoginVO vo = new WxLoginVO();
        vo.setToken(jwtService.issue(user.getId(), JwtService.IDENTITY_USER));
        vo.setNewUser(isNewUser);
        vo.setNeedPrivacyAgree(!configService.get("privacy.policy_version", "v1.0").equals(user.getPrivacyVersion()));
        vo.setCreditScore(user.getCreditScore());

        WxLoginVO.Profile profileVO = new WxLoginVO.Profile();
        profileVO.setNickname(profile == null ? null : profile.getNickname());
        profileVO.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        profileVO.setUiMode(user.getUiMode());
        profileVO.setMotionEnabled(user.getMotionEnabled());
        vo.setProfile(profileVO);

        WxLoginVO.RuntimeConfig config = new WxLoginVO.RuntimeConfig();
        config.setRetentionDays(configService.getInt("compliance.retention_days", 180));
        config.setElderAgeThreshold(configService.getInt("elder.age_threshold", 60));
        vo.setConfig(config);
        return vo;
    }
}