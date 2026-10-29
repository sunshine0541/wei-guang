package com.weiguang.modules.assistant.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 助手响应：回复文本 + 当前动作 + 可选草稿/地址/确认令牌。
 * action: NONE / COLLECTING / NEED_ADDRESS / NEED_PROFILE / READY_TO_CONFIRM / PUBLISHED / ERROR / EXPIRED
 */
@Data
public class AssistantChatResponse {

    private String sessionId;
    private String intent;
    private String reply;
    private String action;
    private PublishDraftVO draft;
    private List<String> missing = new ArrayList<>();
    private List<AddressOptionVO> addressOptions;
    private String confirmToken;
    private Long orderId;
}
