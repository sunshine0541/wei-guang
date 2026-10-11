package com.weiguang.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 能力二 · 善意守护出参（详设 §5.3 JSON Schema）：{riskLevel, hitWords[], suggestion}。
 * degraded=true 表示 AI 不可用（复核未生效，管线应维持 HIGHLIGHT 不升级）。
 */
@Data
public class ReviewResult {

    /** NONE / WARN / HIGH */
    private String riskLevel;

    /** 命中的风险要素词 */
    private List<String> hitWords;

    /** ≤100 字处置建议 */
    private String suggestion;

    /** AI 降级标记（Mock 模式为 false） */
    private boolean degraded;
}