package com.weiguang.modules.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 管理端登录出参（详设 §3.4）。
 */
@Data
@AllArgsConstructor
public class AdminLoginVO {

    private String token;
    private String username;
}
