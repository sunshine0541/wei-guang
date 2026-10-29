package com.weiguang.modules.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 助手草稿编辑入参：只允许修改当前会话里已有的草稿，确认令牌会作废并重新计算。
 */
@Data
public class AssistantDraftUpdateRequest {

    @NotBlank(message = "参数校验失败")
    private String sessionId;

    private String title;
    private String description;
    /** HOME_VISIT / ACCOMPANY / REMOTE */
    private String orderType;
    /** ASAP / TODAY / TOMORROW / THIS_WEEK / ANY */
    private String timeExpect;
    private Long addressId;
    /** 0 / 1 */
    private Integer urgentFlag;
}
