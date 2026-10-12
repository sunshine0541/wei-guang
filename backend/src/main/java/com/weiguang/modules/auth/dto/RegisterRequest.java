package com.weiguang.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * POST /api/auth/register 入参（D31）。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "参数校验失败")
    private String username;

    @NotBlank(message = "参数校验失败")
    private String password;
}