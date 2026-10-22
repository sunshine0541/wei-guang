package com.weiguang.ai.impl;

import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Mock AI 实现（详设 §5.5，演示/无网时默认启用）：本地规则与模板，同一输入返回稳定结果。
 * 能力一走 §5.2 的本地降级规则；能力二按"含发我/告诉我/给我 且命中防诈要素 → HIGH"；能力三返回兜底模板；
 * degraded=false（mock 是正式演示模式，非降级）。
 */
@Service
public class MockAiService implements AiService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final int TAG_MAX = 3;
    private static final double GUESS_CONFIDENCE = 0.3;
    private static final String GUESS_REASON = "根据您的描述猜的，您可以改";
    /** 感谢信兜底模板长度上限（ai.letter_max_length 默认值） */
    private static final int LETTER_MAX_LENGTH = 200;
    /** 防诈要素（详设 §2.21 seed，与 sensitive_word action=REVIEW 的 8 条一致） */
    private static final List<String> FRAUD_ELEMENTS = List.of(
            "验证码", "银行卡", "密码", "转账", "汇款", "扫码付款", "身份证号", "社保卡");

    @Override
    public UnderstandHelpResult understandHelp(String text) {
        String content = text == null ? "" : text.trim();

        UnderstandHelpResult result = new UnderstandHelpResult();
        result.setDegraded(false);

        if (containsAny(content, "买", "取", "药")) {
            result.setOrderType("HOME_VISIT");
            result.setTags(List.of("跑腿"));
            result.setUrgencySuggest("NORMAL");
            result.setTitle(truncate(content, TITLE_MAX_LENGTH));
            result.setConfidence(0.7);
            result.setReason("看起来是跑腿类的小事");
            return result;
        }
        if (containsAny(content, "手机", "视频", "扫码")) {
            result.setOrderType("REMOTE");
            result.setTags(List.of("教学"));
            result.setUrgencySuggest("NORMAL");
            result.setTitle(truncate(content, TITLE_MAX_LENGTH));
            result.setConfidence(0.7);
            result.setReason("看起来是教学类的小事");
            return result;
        }
        if (containsAny(content, "陪", "散步", "聊")) {
            result.setOrderType("ACCOMPANY");
            result.setTags(List.of("陪伴"));
            result.setUrgencySuggest("NORMAL");
            result.setTitle(truncate(content, TITLE_MAX_LENGTH));
            result.setConfidence(0.7);
            result.setReason("看起来是陪伴同行类的小事");
            return result;
        }

        result.setOrderType("REMOTE");
        result.setTags(List.of("其他"));
        result.setUrgencySuggest("NORMAL");
        result.setTitle(truncate(content, TITLE_MAX_LENGTH));
        result.setConfidence(GUESS_CONFIDENCE);
        result.setReason(GUESS_REASON);
        return result;
    }

    @Override
    public ThankLetterDraftResult draftThankLetter(String orderTitle, String requesterName, String helperName) {
        ThankLetterDraftResult result = new ThankLetterDraftResult();
        result.setLetterDraft(letterFallback(orderTitle, helperName));
        result.setDegraded(false);
        return result;
    }

    /** 能力二 mock（详设 §5.5 写死）：含"发我/告诉我/给我"且命中防诈要素 → HIGH，其余 NONE */
    @Override
    public ReviewResult reviewChat(String text, String direction) {
        String content = text == null ? "" : text;
        List<String> hits = new ArrayList<>();
        for (String element : FRAUD_ELEMENTS) {
            if (content.contains(element)) {
                hits.add(element);
            }
        }
        ReviewResult result = new ReviewResult();
        result.setHitWords(hits);
        result.setDegraded(false);
        boolean asking = content.contains("发我") || content.contains("告诉我") || content.contains("给我");
        if (!hits.isEmpty() && asking) {
            result.setRiskLevel("HIGH");
            result.setSuggestion("请不要把验证码、银行卡、密码等信息告诉任何人");
        } else {
            result.setRiskLevel("NONE");
            result.setSuggestion("暂未发现索要敏感信息的风险");
        }
        return result;
    }

    /** 能力三兜底模板（详设 §5.4）："{helperName}，谢谢您帮我"{title}"。有您这样的邻居，真好。"（≤200 字） */
    static String letterFallback(String orderTitle, String helperName) {
        String name = helperName == null || helperName.isBlank() ? "邻居" : helperName;
        String title = orderTitle == null ? "" : orderTitle;
        String text = name + "，谢谢您帮我\"" + title + "\"。有您这样的邻居，真好。";
        return text.length() <= LETTER_MAX_LENGTH ? text : text.substring(0, LETTER_MAX_LENGTH);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String truncate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
