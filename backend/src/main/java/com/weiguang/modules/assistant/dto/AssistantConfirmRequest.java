package com.weiguang.modules.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 助手确认发布入参：confirmToken 必须来自 READY_TO_CONFIRM 响应，一次性使用。
 */
@Data
public class AssistantConfirmRequest {

    @NotBlank(message = "参数校验失败")
    private String sessionId;

    @NotBlank(message = "参数校验失败")
    private String confirmToken;
}
