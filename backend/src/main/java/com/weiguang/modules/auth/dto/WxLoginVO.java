package com.weiguang.modules.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * A1 微信静默登录出参（详设 §3.3）：
 * {token, isNewUser, needPrivacyAgree, profile:{nickname,avatarUrl,uiMode,motionEnabled}, creditScore}
 * config 为追加字段：小程序端需要运行时插值的 sys_config 值（详设 §6.2 P01/P02 文案插值与模式预置），
 * 详见 docs/实现偏差清单.md D02。
 */
@Data
public class WxLoginVO {

    private String token;

    @JsonProperty("isNewUser")
    private boolean newUser;

    private boolean needPrivacyAgree;

    private Profile profile;

    private Integer creditScore;

    private RuntimeConfig config;

    @Data
    public static class Profile {
        private String nickname;
        private String avatarUrl;
        private String uiMode;
        private Integer motionEnabled;
    }

    @Data
    public static class RuntimeConfig {
        /** compliance.retention_days：P01 隐私文案 / P18 注销文案插值 */
        private Integer retentionDays;
        /** elder.age_threshold：P02 年长模式预置阈值 */
        private Integer elderAgeThreshold;
    }
}
