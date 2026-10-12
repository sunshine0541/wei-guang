package com.weiguang.modules.admin.dto;

import lombok.Data;

/**
 * 管理端订单详情中的参与方简要信息（admin 可见全部，含账号状态）。
 */
@Data
public class AdminUserBriefVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
    /** NORMAL/BANNED/CANCELLED */
    private String status;
}