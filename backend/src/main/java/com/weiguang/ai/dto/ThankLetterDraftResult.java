package com.weiguang.ai.dto;

import lombok.Data;

/**
 * 能力三 · 温暖表达出参（详设 §5.4 JSON Schema / §3.3 S2 响应）：
 * {letterDraft ≤ ai.letter_max_length, degraded}。
 */
@Data
public class ThankLetterDraftResult {

    /** 感谢信草稿（≤ ai.letter_max_length，默认 200 字） */
    private String letterDraft;

    /** AI 降级标记（含合规校验不合格改用兜底模板的情形） */
    private boolean degraded;
}