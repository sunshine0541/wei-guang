package com.weiguang.modules.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * GET /admin/users 列表项（详设 §3.4）。
 */
@Data
public class AdminUserVO {

    private Long id;
    private String openid;
    private String nickname;
    private String avatarUrl;
    private Integer age;
    private String uiMode;
    private String status;
    private Integer creditScore;
    private String lightState;
    private LocalDateTime createdAt;
}