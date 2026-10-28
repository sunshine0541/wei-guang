package com.weiguang.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * 能力四 · 内容审核结果（详设 §15.1.6）：SAFE/RISKY + 命中词 + 建议。
 */
@Data
public class ModerationResult {

    /** SAFE/RISKY */
    private String riskLevel;

    private List<String> hitWords;

    private String suggestion;

    /** AI 降级标记（业务层据此走放行 + 标记策略） */
    private boolean degraded;
}
