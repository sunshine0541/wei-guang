package com.weiguang.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.ai.AiSanitizer;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ModerationResult;
import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.common.constants.HelpTagConstants;
import com.weiguang.config.BizMetrics;
import com.weiguang.modules.system.service.ConfigService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 真实 AI 实现公共管线（详设 §5.1）：脱敏 → 调用（超时 ai.timeout_ms / 重试 ai.retry）→ Schema 校验
 * → 标签词表收敛 → 降级 → 元数据日志。任何异常不上抛业务层：返回本地规则/兜底模板结果并置 degraded=true，
 * 同时 weiguang.ai.degraded.total +1。
 * 调用日志只记元数据（能力、耗时、成功/降级、模型名、token 用量），不落原文、不落脱敏前后文本。
 */
@Slf4j
public abstract class AbstractRealAiService implements AiService {

    private static final int TITLE_MAX_LENGTH = 20;
    private static final int REASON_MAX_LENGTH = 100;
    private static final int TAG_MAX = 3;
    private static final Set<String> ORDER_TYPES = Set.of("HOME_VISIT", "ACCOMPANY", "REMOTE");
    private static final Set<String> URGENCY_LEVELS = Set.of("NOT_URGENT", "NORMAL", "URGENT");
    private static final Set<String> RISK_LEVELS = Set.of("NONE", "WARN", "HIGH");
    private static final int SUGGESTION_MAX_LENGTH = 100;
    /** 能力三合规校验词（详设 §5.4）：命中即视为不合格输出，改用兜底模板 */
    private static final List<String> LETTER_FORBIDDEN_WORDS = List.of("转账", "代付", "银行卡", "验证码");

    /** 能力一系统提示（详设 §5.2 Prompt 要点） */
    private static final String UNDERSTAND_SYSTEM = "你是社区邻里互助助手。"
            + "从用户一句话描述中提取：求助类型（HOME_VISIT 上门 / ACCOMPANY 陪同外出 / REMOTE 远程）、"
            + "标签（只能从词表选：跑腿、维修、家务、陪伴、散步、教学、网上办事、宠物花草、聊天、其他，最多 3 个）、"
            + "若词表都不合适，可在 extraTag 给出一个不超过 10 字的自定义标签（否则给空字符串）、"
            + "紧急度建议（NOT_URGENT 不急 / NORMAL 一般 / URGENT 急）、不超过 20 字的规范标题。"
            + "涉生命安全/医疗/法律等超出邻里互助范围的内容，urgencySuggest 给 URGENT 并在 reason 中提示\"建议直接拨打 120/110\"。"
            + "只输出严格 JSON，不输出多余文字："
            + "{\"orderType\":\"...\",\"tags\":[\"...\"],\"extraTag\":\"...\",\"urgencySuggest\":\"...\",\"title\":\"...\",\"confidence\":0.0-1.0,\"reason\":\"...\"}";

    /** 能力三系统提示（详设 §5.4 Prompt 要点） */
    private static final String LETTER_SYSTEM = "你是社区邻里互助助手。以受助者口吻写一封简短感谢信初稿："
            + "真诚、口语、不油腻；不提钱、不做承诺、不涉及隐私、不诱导代操作。"
            + "只输出严格 JSON，不输出多余文字：{\"letterDraft\":\"...\"}";

    /** 能力二系统提示（详设 §5.3 / §15.4 Prompt 要点，双向不区分角色） */
    private static final String REVIEW_SYSTEM = "你是社区邻里互助平台的善意守护助手。"
            + "判断发送方是否在索要对方的验证码/银行卡/密码/转账/汇款/扫码付款/身份证号/社保卡等敏感信息，或诱导对方转账、垫付、代付。"
            + "发送方可能是求助者，也可能是助人者；无论哪一方，索要敏感信息或诱导对方出钱都属于高危。"
            + "正常陪同去银行、教对方自己操作、明确表示不用给钱不算高危。"
            + "只输出严格 JSON，不输出多余文字："
            + "{\"riskLevel\":\"NONE|WARN|HIGH\",\"hitWords\":[\"...\"],\"suggestion\":\"...\"}";

    /** 能力四系统提示（详设 §15.1.6 Prompt 要点） */
    private static final String MODERATION_SYSTEM = "你是社区邻里互助平台的内容审核助手。"
            + "判断用户发布的社区动态或评论是否适合在邻里间公开展示。"
            + "识别：色情低俗、辱骂攻击、违法交易（赌博/刷单/私下交易）、诈骗引流、个人隐私（身份证号/银行卡/手机号/详细门牌）、"
            + "医疗金融法律等专业断言；求助类内容本身不算违规，但 suggestion 中提示\"需要帮忙请去发布求助\"。"
            + "只输出严格 JSON，不输出多余文字："
            + "{\"riskLevel\":\"SAFE|RISKY\",\"hitWords\":[\"...\"],\"suggestion\":\"...\"}";

    protected final AiSanitizer sanitizer;
    protected final MockAiService mockFallback;
    protected final ConfigService configService;
    protected final BizMetrics bizMetrics;
    protected final ObjectMapper objectMapper;

    protected AbstractRealAiService(AiSanitizer sanitizer, MockAiService mockFallback,
                                    ConfigService configService, BizMetrics bizMetrics, ObjectMapper objectMapper) {
        this.sanitizer = sanitizer;
        this.mockFallback = mockFallback;
        this.configService = configService;
        this.bizMetrics = bizMetrics;
        this.objectMapper = objectMapper;
    }

    /** 单次模型调用（具体实现：SpringAI Alibaba / RestClient 直连）；异常由本类统一兜底 */
    protected abstract ModelCallResult invokeModel(String systemPrompt, String userPrompt, String model);

    @Override
    public UnderstandHelpResult understandHelp(String text) {
        String model = configService.get("ai.model.understand", "qwen3.8-flash");
        long start = System.currentTimeMillis();
        try {
            ModelCallResult call = callWithRetry(UNDERSTAND_SYSTEM, sanitizer.sanitize(text), model);
            JsonNode node = parseJson(call.text);
            String orderType = node.path("orderType").asText("");
            if (!ORDER_TYPES.contains(orderType)) {
                throw new IllegalStateException("orderType 非法：" + orderType);
            }
            String urgency = node.path("urgencySuggest").asText("");
            if (!URGENCY_LEVELS.contains(urgency)) {
                throw new IllegalStateException("urgencySuggest 非法：" + urgency);
            }
            String title = truncate(node.path("title").asText("").trim(), TITLE_MAX_LENGTH);
            if (title.isEmpty()) {
                throw new IllegalStateException("title 缺失");
            }
            double confidence = node.path("confidence").asDouble(-1);
            if (confidence < 0 || confidence > 1) {
                throw new IllegalStateException("confidence 越界：" + confidence);
            }
            UnderstandHelpResult result = new UnderstandHelpResult();
            result.setOrderType(orderType);
            result.setUrgencySuggest(urgency);
            result.setTitle(title);
            result.setConfidence(confidence);
            result.setTags(collapseTags(node.path("tags")));
            result.setReason(truncate(node.path("reason").asText("").trim(), REASON_MAX_LENGTH));
            result.setDegraded(false);
            logMetadata("understandHelp", model, start, false, call, null);
            return result;
        } catch (Exception e) {
            UnderstandHelpResult fallback = mockFallback.understandHelp(text);
            fallback.setDegraded(true);
            bizMetrics.aiDegraded();
            logMetadata("understandHelp", model, start, true, null, e);
            return fallback;
        }
    }

    @Override
    public ThankLetterDraftResult draftThankLetter(String orderTitle, String requesterName, String helperName) {
        String model = configService.get("ai.model.letter", "qwen3.8-flash");
        long start = System.currentTimeMillis();
        String safeTitle = sanitizer.sanitize(orderTitle == null ? "" : orderTitle);
        try {
            String userPrompt = "求助标题：" + safeTitle
                    + "\n受助者称呼：" + safe(requesterName)
                    + "\n助人邻居称呼：" + safe(helperName);
            ModelCallResult call = callWithRetry(LETTER_SYSTEM, userPrompt, model);
            JsonNode node = parseJson(call.text);
            String draft = node.path("letterDraft").asText("").trim();
            if (draft.isEmpty()) {
                throw new IllegalStateException("letterDraft 缺失");
            }
            int maxLength = configService.getInt("ai.letter_max_length", 200);
            draft = truncate(draft, maxLength);
            if (containsForbiddenWord(draft)) {
                // 合规校验（详设 §5.4）：不合格输出改用兜底模板；按"小助手在休息"口径对用户提示
                bizMetrics.aiDegraded();
                logMetadata("draftThankLetter", model, start, true, call, null);
                return fallbackLetter(safeTitle, helperName, true);
            }
            ThankLetterDraftResult result = new ThankLetterDraftResult();
            result.setLetterDraft(draft);
            result.setDegraded(false);
            logMetadata("draftThankLetter", model, start, false, call, null);
            return result;
        } catch (Exception e) {
            bizMetrics.aiDegraded();
            logMetadata("draftThankLetter", model, start, true, null, e);
            return fallbackLetter(safeTitle, helperName, true);
        }
    }

    @Override
    public ReviewResult reviewChat(String text, String direction) {
        String model = configService.get("ai.model.review", "qwen3.7-plus");
        long start = System.currentTimeMillis();
        String safeText = sanitizer.sanitize(text);
        try {
            String userPrompt = "direction=" + direction + "\n消息内容：" + safeText;
            ModelCallResult call = callWithRetry(REVIEW_SYSTEM, userPrompt, model);
            JsonNode node = parseJson(call.text);
            String riskLevel = node.path("riskLevel").asText("");
            if (!RISK_LEVELS.contains(riskLevel)) {
                throw new IllegalStateException("riskLevel 非法：" + riskLevel);
            }
            ReviewResult result = new ReviewResult();
            result.setRiskLevel(riskLevel);
            List<String> hitWords = new ArrayList<>();
            JsonNode hitNode = node.path("hitWords");
            if (hitNode.isArray()) {
                for (JsonNode item : hitNode) {
                    String word = item.asText("").trim();
                    if (!word.isEmpty()) {
                        hitWords.add(word);
                    }
                }
            }
            result.setHitWords(hitWords);
            result.setSuggestion(truncate(node.path("suggestion").asText("").trim(), SUGGESTION_MAX_LENGTH));
            result.setDegraded(false);
            logMetadata("reviewChat", model, start, false, call, null);
            return result;
        } catch (Exception e) {
            ReviewResult fallback = mockFallback.reviewChat(text, direction);
            fallback.setDegraded(true);
            bizMetrics.aiDegraded();
            logMetadata("reviewChat", model, start, true, null, e);
            return fallback;
        }
    }

    /** 能力四 · 内容审核（详设 §15.1.6）：异常时回退 mock 并置 degraded=true */
    @Override
    public ModerationResult moderateContent(String contentType, String text) {
        String model = configService.get("ai.model.review", "qwen3.7-plus");
        long start = System.currentTimeMillis();
        String safeText = sanitizer.sanitize(text);
        try {
            String userPrompt = "contentType=" + contentType + "\n内容：" + safeText;
            ModelCallResult call = callWithRetry(MODERATION_SYSTEM, userPrompt, model);
            JsonNode node = parseJson(call.text);
            String riskLevel = node.path("riskLevel").asText("");
            if (!"SAFE".equals(riskLevel) && !"RISKY".equals(riskLevel)) {
                throw new IllegalStateException("riskLevel 非法：" + riskLevel);
            }
            ModerationResult result = new ModerationResult();
            result.setRiskLevel(riskLevel);
            List<String> hitWords = new ArrayList<>();
            JsonNode hitNode = node.path("hitWords");
            if (hitNode.isArray()) {
                for (JsonNode item : hitNode) {
                    String word = item.asText("").trim();
                    if (!word.isEmpty()) {
                        hitWords.add(word);
                    }
                }
            }
            result.setHitWords(hitWords);
            result.setSuggestion(truncate(node.path("suggestion").asText("").trim(), SUGGESTION_MAX_LENGTH));
            result.setDegraded(false);
            logMetadata("moderateContent", model, start, false, call, null);
            return result;
        } catch (Exception e) {
            ModerationResult fallback = mockFallback.moderateContent(contentType, text);
            fallback.setDegraded(true);
            bizMetrics.aiDegraded();
            logMetadata("moderateContent", model, start, true, null, e);
            return fallback;
        }
    }

    /** 超时/失败重试：最多 ai.retry 次（默认 1 次） */
    private ModelCallResult callWithRetry(String system, String user, String model) {
        int retry = configService.getInt("ai.retry", 1);
        RuntimeException last = null;
        for (int i = 0; i <= retry; i++) {
            try {
                return invokeModel(system, user, model);
            } catch (RuntimeException e) {
                last = e;
            }
        }
        throw last;
    }

    /** 解析模型输出：容错 ```json 包裹与前后说明文字 */
    private JsonNode parseJson(String raw) throws Exception {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLine = text.indexOf('\n');
            if (firstLine > 0) {
                text = text.substring(firstLine + 1);
            }
            if (text.endsWith("```")) {
                text = text.substring(0, text.length() - 3);
            }
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1);
        }
        return objectMapper.readTree(text);
    }

    /** 标签词表收敛（详设 §5.2）：非词表内容 → "其他"、去重、≤3 */
    private List<String> collapseTags(JsonNode tagsNode) {
        Set<String> tags = new LinkedHashSet<>();
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode item : tagsNode) {
                String value = item.asText("").trim();
                if (value.isEmpty()) {
                    continue;
                }
                tags.add(HelpTagConstants.TAGS.contains(value) ? value : "其他");
                if (tags.size() >= TAG_MAX) {
                    break;
                }
            }
        }
        if (tags.isEmpty()) {
            tags.add("其他");
        }
        return new ArrayList<>(tags);
    }

    private boolean containsForbiddenWord(String draft) {
        for (String word : LETTER_FORBIDDEN_WORDS) {
            if (draft.contains(word)) {
                return true;
            }
        }
        return false;
    }

    private ThankLetterDraftResult fallbackLetter(String title, String helperName, boolean degraded) {
        ThankLetterDraftResult result = new ThankLetterDraftResult();
        result.setLetterDraft(MockAiService.letterFallback(title, helperName));
        result.setDegraded(degraded);
        return result;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    /** 元数据日志（详设 §5.1）：只记能力/模型/耗时/降级状态/token 用量与异常类型，不落任何文本 */
    private void logMetadata(String capability, String model, long start, boolean degraded,
                             ModelCallResult call, Exception error) {
        log.info("ai capability={} model={} cost={}ms degraded={} promptTokens={} completionTokens={} error={}",
                capability, model, System.currentTimeMillis() - start, degraded,
                call == null ? "-" : call.promptTokens, call == null ? "-" : call.completionTokens,
                error == null ? "-" : error.getClass().getSimpleName());
    }

    /** 模型调用结果：正文 + token 用量（供元数据日志） */
    protected static class ModelCallResult {
        final String text;
        final Integer promptTokens;
        final Integer completionTokens;

        ModelCallResult(String text, Integer promptTokens, Integer completionTokens) {
            this.text = text;
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
        }
    }
}