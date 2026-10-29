package com.weiguang.ai.dto;

import lombok.Data;

/**
 * 能力五 · AI 助手意图与问答出参：GUIDE/PUBLISH/UNKNOWN + 回复 + 待发布描述。
 */
@Data
public class AssistantResult {

    /** GUIDE / PUBLISH / UNKNOWN */
    private String intent;

    /** 给用户的自然语言回复（≤300 字） */
    private String reply;

    /** 用户要发布的求助描述（PUBLISH 时使用，≤500 字；无具体内容则为空） */
    private String publishText;

    /** AI 降级标记（Mock 模式为 false） */
    private boolean degraded;
}
