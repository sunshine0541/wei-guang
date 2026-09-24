package com.weiguang.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.auth.dto.WxLoginVO;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * A1 微信静默登录（详设 §3.3）：
 * code2session → openid 建号/识别 → 签发 JWT。
 * 微信接口失败返回 50002 + 温柔文案；建号 + 建 profile 同事务。
 */
@Slf4j
@Service
public class WxAuthService {

    private static final String WX_ERROR_MESSAGE = "网络打了个盹，请再试一次";

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final JwtService jwtService;
    private final ConfigService configService;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String appId;
    private final String appSecret;

    public WxAuthService(UserMapper userMapper,
                         UserProfileMapper userProfileMapper,
                         JwtService jwtService,
                         ConfigService configService,
                         TransactionTemplate transactionTemplate,
                         ObjectMapper objectMapper,
                         @Value("${WECHAT_APPID:}") String appId,
                         @Value("${WECHAT_APP_SECRET:}") String appSecret) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.jwtService = jwtService;
        this.configService = configService;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.appId = appId;
        this.appSecret = appSecret;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public WxLoginVO wxLogin(String code) {
        String openid = code2session(code);

        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getOpenid, openid));
        boolean isNewUser = user == null;
        if (isNewUser) {
            user = transactionTemplate.execute(status -> register(openid));
        }

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

    /** 建号 + 建空画像（同事务；已注销记录不复用，其 openid 已被改写） */
    private User register(String openid) {
        User user = new User();
        user.setOpenid(openid);
        user.setUiMode("NORMAL");
        user.setLightState("UNLIT");
        user.setCreditScore(configService.getInt("credit.init", 100));
        userMapper.insert(user);

        UserProfile profile = new UserProfile();
        profile.setUserId(user.getId());
        userProfileMapper.insert(profile);
        log.info("新用户建号完成 uid={}", user.getId());
        return user;
    }

    private String code2session(String code) {
        WxSessionResponse response;
        try {
            // 微信 code2session 返回 JSON，但响应头为 Content-Type: text/plain，
            // 直接 body(WxSessionResponse.class) 会因找不到转换器而失败，故取原文后自行解析
            String raw = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.weixin.qq.com")
                            .path("/sns/jscode2session")
                            .queryParam("appid", appId)
                            .queryParam("secret", appSecret)
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            response = objectMapper.readValue(raw, WxSessionResponse.class);
        } catch (Exception e) {
            log.warn("微信 code2session 调用异常：{}", e.getMessage());
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, WX_ERROR_MESSAGE);
        }

        boolean failed = response == null
                || (response.getErrcode() != null && response.getErrcode() != 0)
                || response.getOpenid() == null
                || response.getOpenid().isBlank();
        if (failed) {
            log.warn("微信 code2session 失败：errcode={}", response == null ? null : response.getErrcode());
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, WX_ERROR_MESSAGE);
        }
        return response.getOpenid();
    }

    /** code2session 响应（只取所需字段） */
    @Data
    private static class WxSessionResponse {
        private String openid;
        private Integer errcode;
    }
}
