package com.weiguang.ai.impl;

import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.AssistantResult;
import com.weiguang.ai.dto.ModerationResult;
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
    /** 防诈要素（详设 §2.21 seed 8 条 + v1.3 双向补充的"垫付/代付/转给"类诱导词） */
    private static final List<String> FRAUD_ELEMENTS = List.of(
            "验证码", "银行卡", "密码", "转账", "汇款", "扫码付款", "身份证号", "社保卡",
            "垫付", "垫一下", "代付", "转给");

    /** 能力四 mock 风险词（演示用，覆盖色情/辱骂/违法交易/诈骗引流） */
    private static final List<String> MODERATION_RISKY_WORDS = List.of(
            "赌博", "刷单", "加微信私下交易", "色情", "裸聊", "辱骂", "去死", "砍死", "代付", "垫付");

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
        boolean asking = containsAny(content, "发我", "告诉我", "给我", "转给", "垫", "代付");
        if (!hits.isEmpty() && asking) {
            result.setRiskLevel("HIGH");
            result.setSuggestion("请不要把验证码、银行卡、密码等信息告诉任何人");
        } else {
            result.setRiskLevel("NONE");
            result.setSuggestion("暂未发现索要敏感信息的风险");
        }
        return result;
    }

    /** 能力四 mock：命中风险词 → RISKY，否则 SAFE（degraded=false） */
    @Override
    public ModerationResult moderateContent(String contentType, String text) {
        String content = text == null ? "" : text;
        List<String> hits = new ArrayList<>();
        for (String word : MODERATION_RISKY_WORDS) {
            if (content.contains(word)) {
                hits.add(word);
            }
        }
        ModerationResult result = new ModerationResult();
        result.setHitWords(hits);
        result.setDegraded(false);
        if (hits.isEmpty()) {
            result.setRiskLevel("SAFE");
            result.setSuggestion("内容看起来没问题");
        } else {
            result.setRiskLevel("RISKY");
            result.setSuggestion("内容可能不适合公开展示，建议修改后再发");
        }
        return result;
    }

    /** 能力五 mock：规则 FAQ + 发布意图识别；正式演示模式 degraded=false */
    @Override
    public AssistantResult assistant(String message) {
        String content = message == null ? "" : message.trim();
        AssistantResult result = new AssistantResult();
        result.setDegraded(false);

        String faq = faqReply(content);
        if (faq != null) {
            result.setIntent("GUIDE");
            result.setReply(faq);
            return result;
        }

        boolean publishIntent = containsAny(content,
                "发布求助", "发求助", "帮我发", "我要发布", "我想发布", "需要帮助", "需要人帮", "想请人", "帮我找");
        boolean looksLikeRequest = content.length() >= 4
                && containsAny(content, "漏水", "灯不亮", "搬", "陪", "教我用", "取", "买", "修", "打扫", "医院", "药", "照顾")
                && !containsAny(content, "怎么", "如何", "为什么", "？", "?");
        if (publishIntent || looksLikeRequest) {
            String publishText = content.replaceFirst(
                    "^(帮我|我想|我要)?(发布|发)(一个|一条)?(求助|需求)?[：:，,。\\s]*", "").trim();
            if (publishText.length() < 4) {
                result.setIntent("UNKNOWN");
                result.setReply("可以告诉我需要邻居帮什么忙吗？比如“厨房水管漏水，今天想请人上门看看”。");
                return result;
            }
            result.setIntent("PUBLISH");
            result.setReply("好的，我先帮您整理这条求助。");
            result.setPublishText(publishText);
            return result;
        }

        result.setIntent("UNKNOWN");
        result.setReply("我可以回答发布、接单、取消、申诉等操作问题，也可以帮您发布求助。请告诉我您想做什么。");
        return result;
    }

    private String faqReply(String text) {
        if (containsAny(text, "怎么发布", "如何发布", "怎样发布", "发布流程", "发布求助的步骤")) {
            return "发布求助：在首页点「我需要帮助」，选择模板或直接描述，填写类型、时间、地址后发布。"
                    + "上门/陪同类需要先填好紧急联系人。需要我帮您发布求助吗？";
        }
        if (containsAny(text, "怎么接单", "如何接单", "怎样接单", "接单流程", "怎么帮忙")) {
            return "接单：在首页点「我可以帮忙」，选一条求助，点「我愿意帮助」，和对方聊天确认后开始帮忙，"
                    + "完成后由对方确认结算。需要我帮您发布求助吗？";
        }
        if (containsAny(text, "取消", "撤回")) {
            return "待响应的求助可以在「我的求助」撤回；已接单的求助需要和对方协商取消，"
                    + "对方同意或管理员裁决后才会取消。需要我帮您发布求助吗？";
        }
        if (containsAny(text, "确认完成", "怎么确认", "完成确认")) {
            return "对方标记完成后，您在「我的求助」里点确认；超过 24 小时未确认会进入超时处理。需要我帮您发布求助吗？";
        }
        if (containsAny(text, "申诉", "反馈")) {
            return "完成或取消后 7 天内，可以在「反馈与申诉」提交申诉或反馈，也可以上传一张凭证。需要我帮您发布求助吗？";
        }
        if (containsAny(text, "微光值", "积分", "荣誉")) {
            return "完成一次互助会增加微光值和荣誉积分，违规会扣分；具体明细可以在「我的微光值」「我的荣誉积分」查看。"
                    + "需要我帮您发布求助吗？";
        }
        if (containsAny(text, "隐私", "地址", "安全")) {
            return "地址会分级展示：远程不需要地址，陪同只显示集合点，上门门牌要接单后才解锁；AI 调用前也会先脱敏。"
                    + "需要我帮您发布求助吗？";
        }
        return null;
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
