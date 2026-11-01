package com.weiguang.modules.system.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.modules.appeal.service.AppealService;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.entity.Evaluation;
import com.weiguang.modules.incentive.entity.StoryCard;
import com.weiguang.modules.incentive.entity.ThankLetter;
import com.weiguang.modules.incentive.entity.Whisper;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.incentive.mapper.EvaluationMapper;
import com.weiguang.modules.incentive.mapper.StoryCardMapper;
import com.weiguang.modules.incentive.mapper.ThankLetterMapper;
import com.weiguang.modules.incentive.mapper.WhisperMapper;
import com.weiguang.modules.incentive.service.CreditService;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.entity.OrderTimeline;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.mapper.OrderTimelineMapper;
import com.weiguang.modules.order.service.OrderTimelineRecorder;
import com.weiguang.modules.order.service.ReleaseService;
import com.weiguang.modules.order.service.SettleService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 定时任务 J1–J6/J8/J10（详设 §4.3）。单机单实例，全部条件更新保证每单只流转一次；
 * 每个任务打印执行摘要，异常不中断后续批次。
 */
@Slf4j
@Component
public class OrderScheduledTasks {

    private static final String ANONYMIZED = "已注销用户";

    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final StoryCardMapper storyCardMapper;
    private final ThankLetterMapper thankLetterMapper;
    private final WhisperMapper whisperMapper;
    private final EvaluationMapper evaluationMapper;
    private final OrderTimelineMapper orderTimelineMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final ReleaseService releaseService;
    private final SettleService settleService;
    private final NotifyService notifyService;
    private final ConfigService configService;
    private final CreditService creditService;
    private final AppealService appealService;

    public OrderScheduledTasks(HelpOrderMapper helpOrderMapper,
                               UserMapper userMapper,
                               CreditLedgerMapper creditLedgerMapper,
                               StoryCardMapper storyCardMapper,
                               ThankLetterMapper thankLetterMapper,
                               WhisperMapper whisperMapper,
                               EvaluationMapper evaluationMapper,
                               OrderTimelineMapper orderTimelineMapper,
                               ChatMessageMapper chatMessageMapper,
                               OrderTimelineRecorder timelineRecorder,
                               ReleaseService releaseService,
                               SettleService settleService,
                               NotifyService notifyService,
                               ConfigService configService,
                               CreditService creditService,
                               AppealService appealService) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.storyCardMapper = storyCardMapper;
        this.thankLetterMapper = thankLetterMapper;
        this.whisperMapper = whisperMapper;
        this.evaluationMapper = evaluationMapper;
        this.orderTimelineMapper = orderTimelineMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.timelineRecorder = timelineRecorder;
        this.releaseService = releaseService;
        this.settleService = settleService;
        this.notifyService = notifyService;
        this.configService = configService;
        this.creditService = creditService;
        this.appealService = appealService;
    }

    /** J1 待响应超时：每 1 min，PENDING 且 publish_deadline<now → EXPIRED */
    @Scheduled(fixedDelay = 60_000)
    public void j1PendingTimeout() {
        List<HelpOrder> orders = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .lt(HelpOrder::getPublishDeadline, LocalDateTime.now()));
        int moved = 0;
        for (HelpOrder order : orders) {
            if (expire(order, "REPLY_TIMEOUT")) {
                moved++;
            }
        }
        log.info("J1 待响应超时：扫描 {} 单，流转 {} 单", orders.size(), moved);
    }

    /** J2 确认开始超时：每 1 min，ACCEPTED 且 start_deadline<now → 释放重派（−15） */
    @Scheduled(fixedDelay = 60_000)
    public void j2StartTimeout() {
        List<HelpOrder> orders = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.ACCEPTED.name())
                .lt(HelpOrder::getStartDeadline, LocalDateTime.now()));
        int moved = 0;
        for (HelpOrder order : orders) {
            try {
                if (releaseService.releaseByTimeout(order)) {
                    moved++;
                }
            } catch (Exception e) {
                log.warn("J2 单笔处理异常 orderId={}：{}", order.getId(), e.getMessage());
            }
        }
        log.info("J2 确认开始超时：扫描 {} 单，释放 {} 单", orders.size(), moved);
    }

    /** J3 待确认超时：每 1 min，PENDING_CONFIRM 且 confirm_deadline<now → 默认通过并结算 */
    @Scheduled(fixedDelay = 60_000)
    public void j3ConfirmTimeout() {
        List<HelpOrder> orders = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .lt(HelpOrder::getConfirmDeadline, LocalDateTime.now()));
        int moved = 0;
        for (HelpOrder order : orders) {
            try {
                if (settleService.settle(order, 0L)) {
                    moved++;
                }
            } catch (Exception e) {
                log.warn("J3 单笔处理异常 orderId={}：{}", order.getId(), e.getMessage());
            }
        }
        log.info("J3 待确认超时：扫描 {} 单，结算 {} 单", orders.size(), moved);
    }

    /** J4 僵尸单清理：每 1 h，PENDING 且 created_at < now−order.zombie_clean_days → EXPIRED */
    @Scheduled(fixedDelay = 3_600_000)
    public void j4ZombieClean() {
        LocalDateTime before = LocalDateTime.now()
                .minusDays(configService.getInt("order.zombie_clean_days", 7));
        List<HelpOrder> orders = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .lt(HelpOrder::getCreatedAt, before));
        int moved = 0;
        for (HelpOrder order : orders) {
            if (expire(order, "ZOMBIE_CLEAN")) {
                moved++;
            }
        }
        log.info("J4 僵尸单清理：扫描 {} 单，流转 {} 单", orders.size(), moved);
    }

    /** J9 申诉超时兜底：每 1 h，PENDING 申诉超过配置时长 → 恢复订单原状态并关闭申诉 */
    @Scheduled(fixedDelay = 3_600_000)
    public void j9AppealTimeout() {
        int hours = configService.getInt("order.appeal_timeout_hours", 72);
        List<Long> appealIds = appealService.findTimeoutAppealIds(hours);
        int moved = 0;
        for (Long appealId : appealIds) {
            try {
                if (appealService.resolveTimeoutOne(appealId)) {
                    moved++;
                }
            } catch (Exception e) {
                log.warn("J9 单条处理异常 appealId={}：{}", appealId, e.getMessage());
            }
        }
        log.info("J9 申诉超时兜底：阈值 {} 小时，处理 {} 条", hours, moved);
    }

    /** J10 违约计数衰减：超过配置周期没有新违约记录的用户，清零历史计数 */
    @Scheduled(fixedDelay = 3_600_000)
    public void j10ViolationDecay() {
        int days = configService.getInt("order.violation_decay_days", 30);
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        List<CreditLedger> logs = creditLedgerMapper.selectList(Wrappers.<CreditLedger>lambdaQuery()
                .in(CreditLedger::getReasonCode,
                        "ACCEPT_CANCEL", "START_TIMEOUT", "MID_QUIT", "URGENT_ABUSE", "MALICIOUS_CANCEL", "FAKE_ORDER")
                .orderByDesc(CreditLedger::getCreatedAt));
        java.util.Map<Long, LocalDateTime> lastByUser = new java.util.HashMap<>();
        for (CreditLedger log : logs) {
            if (log.getUserId() == null || log.getCreatedAt() == null) {
                continue;
            }
            LocalDateTime last = lastByUser.get(log.getUserId());
            if (last == null || log.getCreatedAt().isAfter(last)) {
                lastByUser.put(log.getUserId(), log.getCreatedAt());
            }
        }
        java.util.Set<Long> decayed = new java.util.HashSet<>();
        lastByUser.forEach((uid, last) -> {
            if (last.isBefore(before)) {
                decayed.add(uid);
            }
        });
        if (decayed.isEmpty()) {
            return;
        }
        int rows = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .in(User::getId, decayed)
                .isNull(User::getCooldownUntil)
                .gt(User::getViolationCount, 0)
                .set(User::getViolationCount, 0));
        log.info("J10 违约计数衰减：{} 天无新违约，清零 {} 人", days, rows);
    }

    /** J5 冷却收尾：每 10 min，cooldown_until<now 且 violation_count>0 → 双字段清零 */
    @Scheduled(fixedDelay = 600_000)
    public void j5CooldownReset() {
        int rows = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .lt(User::getCooldownUntil, LocalDateTime.now())
                .gt(User::getViolationCount, 0)
                .set(User::getViolationCount, 0)
                .set(User::getCooldownUntil, null));
        log.info("J5 冷却收尾：清零 {} 条", rows);
    }

    /** J6 滥用判定：每 1 h，URGENT_ABUSE / MALICIOUS_CANCEL（7 天窗口，同原因不重复扣） */
    @Scheduled(fixedDelay = 3_600_000)
    public void j6AbuseCheck() {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        int urgentThreshold = configService.getInt("abuse.urgent_count_7d", 2);
        int cancelThreshold = configService.getInt("abuse.cancel_count_7d", 3);

        Set<Long> urgentUsers = ids(helpOrderMapper.selectObjs(Wrappers.<HelpOrder>query()
                .select("requester_id")
                .eq("urgent_flag", 1)
                .ge("created_at", since)
                .in("status", OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name())
                .groupBy("requester_id")
                .having("COUNT(*) >= " + urgentThreshold)));
        Set<Long> cancelUsers = ids(helpOrderMapper.selectObjs(Wrappers.<HelpOrder>query()
                .select("requester_id")
                .eq("cancel_by", "REQUESTER")
                .ge("cancelled_at", since)
                .groupBy("requester_id")
                .having("COUNT(*) >= " + cancelThreshold)));

        Set<Long> candidates = new HashSet<>(urgentUsers);
        candidates.addAll(cancelUsers);
        if (candidates.isEmpty()) {
            log.info("J6 滥用判定：命中 0 次");
            return;
        }

        Set<String> deducted = new HashSet<>();
        for (CreditLedger ledger : creditLedgerMapper.selectList(Wrappers.<CreditLedger>lambdaQuery()
                .in(CreditLedger::getUserId, candidates)
                .in(CreditLedger::getReasonCode, "URGENT_ABUSE", "MALICIOUS_CANCEL")
                .ge(CreditLedger::getCreatedAt, since))) {
            deducted.add(ledger.getUserId() + "|" + ledger.getReasonCode());
        }

        int hits = 0;
        for (User user : userMapper.selectBatchIds(candidates)) {
            if (!"NORMAL".equals(user.getStatus())) {
                continue;
            }
            Long uid = user.getId();
            try {
                if (urgentUsers.contains(uid) && !deducted.contains(uid + "|URGENT_ABUSE")) {
                    deduct(uid, "URGENT_ABUSE", configService.getInt("credit.urgent_abuse", -10));
                    hits++;
                }
                if (cancelUsers.contains(uid) && !deducted.contains(uid + "|MALICIOUS_CANCEL")) {
                    deduct(uid, "MALICIOUS_CANCEL", configService.getInt("credit.malicious_cancel", -10));
                    hits++;
                }
            } catch (Exception e) {
                log.warn("J6 用户 {} 滥用判定异常：{}", uid, e.getMessage());
            }
        }
        log.info("J6 滥用判定：候选 {} 人，命中 {} 次", candidates.size(), hits);
    }

    private Set<Long> ids(List<Object> values) {
        Set<Long> ids = new HashSet<>();
        for (Object value : values) {
            if (value instanceof Number number) {
                ids.add(number.longValue());
            }
        }
        return ids;
    }

    /** J8 协商取消超时：每 10 min，应答截止已过且仍在未完成态 → 清空挂起字段（请求失效） */
    @Scheduled(fixedDelay = 600_000)
    public void j8CancelPendingTimeout() {
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .isNotNull(HelpOrder::getCancelPendingBy)
                .lt(HelpOrder::getCancelPendingDeadline, LocalDateTime.now())
                .notIn(HelpOrder::getStatus, OrderStatus.COMPLETED.name(),
                        OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name())
                .set(HelpOrder::getCancelPendingBy, null)
                .set(HelpOrder::getCancelPendingDeadline, null));
        log.info("J8 协商取消超时：失效 {} 条", rows);
    }

    /** J10 留存到期清理：每日 03:30，注销用户文本快照替换为"已注销用户"（账本保留） */
    @Scheduled(cron = "0 30 3 * * *")
    public void j10RetentionClean() {
        LocalDateTime before = LocalDateTime.now()
                .minusDays(configService.getInt("compliance.retention_days", 180));
        List<User> users = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getStatus, "CANCELLED")
                .lt(User::getUpdatedAt, before));
        int cleaned = 0;
        for (User user : users) {
            Long uid = user.getId();
            helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getRequesterId, uid)
                    .set(HelpOrder::getTitle, ANONYMIZED));
            helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getRequesterId, uid)
                    .set(HelpOrder::getDescription, ANONYMIZED));
            helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getRequesterId, uid)
                    .set(HelpOrder::getAddressArea, ANONYMIZED));
            helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getRequesterId, uid)
                    .set(HelpOrder::getAddressDetail, null));
            orderTimelineMapper.update(null, Wrappers.<OrderTimeline>lambdaUpdate()
                    .eq(OrderTimeline::getOperatorId, uid)
                    .set(OrderTimeline::getRemark, ANONYMIZED));
            chatMessageMapper.update(null, Wrappers.<ChatMessage>lambdaUpdate()
                    .eq(ChatMessage::getSenderId, uid)
                    .set(ChatMessage::getContent, ANONYMIZED));
            // v1.3：故事卡姓名快照、感谢信/悄悄话正文、评价建议一并匿名化
            List<Long> relatedOrderIds = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                            .select(HelpOrder::getId)
                            .and(w -> w.eq(HelpOrder::getRequesterId, uid)
                                    .or().eq(HelpOrder::getHelperId, uid)))
                    .stream().map(HelpOrder::getId).toList();
            if (!relatedOrderIds.isEmpty()) {
                storyCardMapper.update(null, Wrappers.<StoryCard>lambdaUpdate()
                        .in(StoryCard::getOrderId, relatedOrderIds)
                        .set(StoryCard::getRequesterName, ANONYMIZED)
                        .set(StoryCard::getHelperName, ANONYMIZED)
                        .set(StoryCard::getThing, ANONYMIZED)
                        .set(StoryCard::getSummary, ANONYMIZED));
            }
            thankLetterMapper.update(null, Wrappers.<ThankLetter>lambdaUpdate()
                    .eq(ThankLetter::getSenderId, uid)
                    .set(ThankLetter::getContent, ANONYMIZED));
            whisperMapper.update(null, Wrappers.<Whisper>lambdaUpdate()
                    .eq(Whisper::getSenderId, uid)
                    .set(Whisper::getContent, ANONYMIZED));
            evaluationMapper.update(null, Wrappers.<Evaluation>lambdaUpdate()
                    .eq(Evaluation::getRequesterId, uid)
                    .set(Evaluation::getAdvice, ANONYMIZED));
            // 条件更新保证只处理一次：处理完把 updated_at 前推
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, uid)
                    .lt(User::getUpdatedAt, before)
                    .set(User::getUpdatedAt, LocalDateTime.now()));
            cleaned++;
        }
        log.info("J10 留存到期清理：扫描 {} 人，清理 {} 人", users.size(), cleaned);
    }

    /** 置 EXPIRED（条件更新）+ 时间线 + 通知 */
    private boolean expire(HelpOrder order, String remark) {
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .set(HelpOrder::getStatus, OrderStatus.EXPIRED.name()));
        if (rows == 0) {
            return false;
        }
        timelineRecorder.record(order.getId(), "EXPIRE", 0L, remark);
        notifyService.expired(order.getRequesterId(), order.getTitle(), order.getId());
        return true;
    }

    private void deduct(Long uid, String reasonCode, int delta) {
        creditService.apply(uid, delta, reasonCode, null, 0L, null);
        notifyService.creditChanged(uid);
    }
}