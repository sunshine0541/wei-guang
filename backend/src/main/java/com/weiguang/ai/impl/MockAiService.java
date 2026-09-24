package com.weiguang.ai.impl;

import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.UnderstandHelpResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Mock AI 实现（详设 §5.5，演示/无网时默认启用）：本地规则与模板，同一输入返回稳定结果。
 * 能力一走 §5.2 的本地降级规则；degraded=false（mock 是正式演示模式，非降级）。
 */
@Service
public class MockAiService implements AiService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final int TAG_MAX = 3;
    private static final double GUESS_CONFIDENCE = 0.3;
    private static final String GUESS_REASON = "根据您的描述猜的，您可以改";

    @Override
    public UnderstandHelpResult understandHelp(String text) {
        String content = text == null ? "" : text.trim();

        UnderstandHelpResult result = new UnderstandHelpResult();
        result.setDegraded(false);

        if (containsAny(content, "买", "取", "药")) {
            result.setOrderType("HOME_VISIT");
            result.setTags(List.of("跑腿代买"));
            result.setUrgencySuggest("NORMAL");
            result.setTitle(truncate(content, TITLE_MAX_LENGTH));
            result.setConfidence(0.7);
            result.setReason("看起来是跑腿代买类的小事");
            return result;
        }
        if (containsAny(content, "手机", "视频", "扫码")) {
            result.setOrderType("REMOTE");
            result.setTags(List.of("手机教学"));
            result.setUrgencySuggest("NORMAL");
            result.setTitle(truncate(content, TITLE_MAX_LENGTH));
            result.setConfidence(0.7);
            result.setReason("看起来是手机教学类的小事");
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
