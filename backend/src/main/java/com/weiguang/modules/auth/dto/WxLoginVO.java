package com.weiguang.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A1 微信静默登录出参（详设 §3.3）：
 * {token, isNewUser, needPrivacyAgree, profile:{nickname,avatarUrl,uiMode,motionEnabled}, creditScore}
 */
@Data
public class WxLoginVO {

    private String token;

    @JsonProperty("isNewUser")
    private boolean newUser;

    private boolean needPrivacyAgree;

    private Profile profile;

    private Integer creditScore;

    @Data
    public static class Profile {
        private String nickname;
        private String avatarUrl;
        private String uiMode;
        private Integer motionEnabled;
    }
}
