package com.weiguang.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 能力一 · 求助理解出参（详设 §5.2 JSON Schema / §3.3 O1 响应）。
 */
@Data
public class UnderstandHelpResult {

    /** HOME_VISIT / ACCOMPANY / REMOTE */
    private String orderType;

    /** 受控标签词表子集，≤3 个 */
    private List<String> tags;

    /** NOT_URGENT / NORMAL / URGENT */
    private String urgencySuggest;

    /** 不超过 20 字的规范标题 */
    private String title;

    /** 0–1 */
    private Double confidence;

    /** ≤100 字，低置信度时给用户解释 */
    private String reason;

    /** AI 降级标记（Mock 模式为 false） */
    private boolean degraded;
}
