package com.weiguang.modules.user.dto;

import lombok.Data;

import java.util.List;

/**
 * U1 GET /api/user/profile 出参（详设 §3.6）。
 */
@Data
public class UserProfileVO {

    private String nickname;
    private String avatarUrl;
    private Integer age;
    private String signature;
    private List<String> hobbies;
    private String careTip;
    private List<String> helpDirections;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String phone;
    private String uiMode;
    private Integer motionEnabled;
    private Integer creditScore;
    private String lightState;
    private Integer lightPassed;
    private Boolean hasPrivacyAgreed;
    /** 注销文案天数插值（D31：账密登录冷启动不再经过 A1，随 U1 一并下发） */
    private Integer retentionDays;
    /** 年长模式年龄阈值（同上） */
    private Integer elderAgeThreshold;
}
