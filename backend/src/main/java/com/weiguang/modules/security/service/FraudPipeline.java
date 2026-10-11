package com.weiguang.modules.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.config.BizMetrics;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.security.entity.SensitiveWord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 防诈管线（详设 §5.3，分层规则写死）：
 * 1) 本地扫描（同步）：命中 HIGHLIGHT 词 → 高亮预警；命中 FRAUD/REVIEW 要素且方向为助人者→求助者 → 交能力二异步复核。
 * 2) AI 复核（异步）：确认 HIGH → risk_flag=STRONG + report 落库 + WS 强提示 + 计数器 +1；
 *    AI 不可用/非 HIGH → 维持 HIGHLIGHT（不升级、不误拦）。
 */
@Slf4j
@Service
public class FraudPipeline {

    public static final String FLAG_NONE = "NONE";
    public static final String FLAG_HIGHLIGHT = "HIGHLIGHT";
    public static final String FLAG_STRONG = "STRONG";
    public static final String DIRECTION_HELPER_TO_REQUESTER = "HELPER_TO_REQUESTER";
    /** 强提示文案·受保护方（详设 §5.3，写死不得改写） */
    public static final String ALERT_MESSAGE =
            "为了您的安全，请不要把验证码、银行卡、密码等信息告诉任何人。真正的邻居不会向您索要这些。";
    /** 强提示文案·发送方回执（详设未定义，T14 需求方反馈新增，见《实现偏差清单》D25） */
    public static final String SENDER_RECEIPT_MESSAGE =
            "您发送的内容涉及敏感信息，请避免向他人索要验证码、银行卡、密码等。真正的邻里互助不涉及这些。";

    private final SensitiveWordService sensitiveWordService;
    private final AiService aiService;
    private final ChatMessageMapper chatMessageMapper;
    private final ReportMapper reportMapper;
    private final WsSessionRegistry wsSessionRegistry;
    private final BizMetrics bizMetrics;

    public FraudPipeline(SensitiveWordService sensitiveWordService, AiService aiService,
                         ChatMessageMapper chatMessageMapper, ReportMapper reportMapper,
                         WsSessionRegistry wsSessionRegistry, BizMetrics bizMetrics) {
        this.sensitiveWordService = sensitiveWordService;
        this.aiService = aiService;
        this.chatMessageMapper = chatMessageMapper;
        this.reportMapper = reportMapper;
        this.wsSessionRegistry = wsSessionRegistry;
        this.bizMetrics = bizMetrics;
    }

    /** 本地分级结果：riskFlag（NONE/HIGHLIGHT）、是否需要复核、命中的防诈要素 */
    public record LocalDecision(String riskFlag, boolean reviewRequired, List<String> hitWords) {
    }

    /** 同步本地分级（详设 §5.3 第 1、2 层） */
    public LocalDecision evaluateLocal(String text, boolean helperToRequester) {
        List<SensitiveWord> hits = sensitiveWordService.scan(text);
        List<String> reviewWords = hits.stream()
                .filter(word -> SensitiveWordService.ACTION_REVIEW.equals(word.getAction()))
                .map(SensitiveWord::getWord)
                .toList();
        boolean highlight = hits.stream()
                .anyMatch(word -> SensitiveWordService.ACTION_HIGHLIGHT.equals(word.getAction()));
        if (reviewWords.isEmpty()) {
            return new LocalDecision(highlight ? FLAG_HIGHLIGHT : FLAG_NONE, false, List.of());
        }
        // 防诈要素命中：初始 HIGHLIGHT（AI 不可用时兜底不升级）；仅"助人者→求助者"方向交复核
        return new LocalDecision(FLAG_HIGHLIGHT, helperToRequester, reviewWords);
    }

    /**
     * 异步复核（详设 §5.3 第 3 层；事务外）：
     * HIGH 且未降级 → 聊天留档 STRONG + report 落库（证据=命中要素与方向）+ WS 强提示 + weiguang.fraud.hit.total+1。
     */
    @Async
    public void reviewAsync(Long orderId, Long messageId, Long senderId, Long requesterId,
                            String text, List<String> hitWords) {
        try {
            ReviewResult result = aiService.reviewChat(text, DIRECTION_HELPER_TO_REQUESTER);
            if (result.isDegraded() || !"HIGH".equals(result.getRiskLevel())) {
                // AI 不可用/非高危：维持 HIGHLIGHT 兜底（不升级 STRONG、不生成 report）
                return;
            }
            chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                    .eq(ChatMessage::getId, messageId)
                    .ne(ChatMessage::getRiskFlag, FLAG_STRONG)
                    .set(ChatMessage::getRiskFlag, FLAG_STRONG));
            Report report = new Report();
            report.setOrderId(orderId);
            report.setMessageId(messageId);
            report.setTargetUserId(senderId);
            report.setType("FRAUD_RISK");
            report.setEvidence("命中要素：" + String.join("、", hitWords) + "；方向：HELPER→REQUESTER");
            report.setStatus("PENDING");
            report.setCreatedAt(LocalDateTime.now());
            reportMapper.insert(report);
            // WS 强提示：受保护方=警示（§5.3 文案）、发送方=回执（文案分流，见《实现偏差清单》D25）
            wsSessionRegistry.pushAsync(requesterId, WsEnvelope.fraudAlert(ALERT_MESSAGE, messageId));
            wsSessionRegistry.pushAsync(senderId, WsEnvelope.fraudAlert(SENDER_RECEIPT_MESSAGE, messageId));
            bizMetrics.fraudHit();
            log.info("防诈高危命中 messageId={} orderId={} 要素={}", messageId, orderId, String.join("、", hitWords));
        } catch (Exception e) {
            log.warn("防诈复核异常 messageId={}：{}", messageId, e.getClass().getSimpleName());
        }
    }
}