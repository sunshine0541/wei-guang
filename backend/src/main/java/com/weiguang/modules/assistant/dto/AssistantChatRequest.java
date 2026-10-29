package com.weiguang.modules.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 助手对话入参：message 必填；地址/时间/急标用于前端快捷选择回填。
 */
@Data
public class AssistantChatRequest {

    /** 会话 ID；为空则新建 */
    private String sessionId;

    @NotBlank(message = "参数校验失败")
    private String message;

    /** 用户在地址选择卡中选中的地址 */
    private Long addressId;

    /** 用户快捷选择的时间期望：ASAP/TODAY/TOMORROW/THIS_WEEK/ANY */
    private String timeExpect;

    /** 用户快捷选择是否急单：0/1 */
    private Integer urgentFlag;
}
