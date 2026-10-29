package com.weiguang.modules.assistant.dto;

import lombok.Data;

/**
 * AI 助手会话（存 Redis，2 小时 TTL；Redis 不可用时降级本地内存）。
 */
@Data
public class AssistantSession {

    private String sessionId;
    private Long userId;
    private PublishDraftVO draft;
    private String stage;
    private String confirmToken;
    /** 上一轮已追问“具体需要邻居帮什么忙”，下一轮短描述可直接进入识别 */
    private boolean awaitingDescription;
    private long updatedAt;
}
