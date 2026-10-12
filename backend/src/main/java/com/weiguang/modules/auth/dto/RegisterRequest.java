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

    /** 注册页勾选"已阅读并同意隐私说明"（必填 true；服务端落 A3 同意记录，D31 隐私入口变更） */
    private Boolean privacyAgreed;
}