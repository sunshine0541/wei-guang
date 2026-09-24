package com.weiguang.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端登录入参（详设 §3.4）。
 */
@Data
public class AdminLoginRequest {

    @NotBlank(message = "参数校验失败")
    private String username;

    @NotBlank(message = "参数校验失败")
    private String password;
}
