package com.weiguang.modules.system.task;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
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
import java.util.List;

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
    private final OrderTimelineMapper orderTimelineMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final ReleaseService releaseService;
    private final SettleService settleService;
    private final NotifyService notifyService;
    private final ConfigService configService;

    public OrderScheduledTasks(HelpOrderMapper helpOrderMapper,
                               UserMapper userMapper,
                               CreditLedgerMapper creditLedgerMapper,
                               OrderTimelineMapper orderTimelineMapper,
                               ChatMessageMapper chatMessageMapper,
                               OrderTimelineRecorder timelineRecorder,
                               ReleaseService releaseService,
                               SettleService settleService,
                               NotifyService notifyService,
                               ConfigService configService) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.orderTimelineMapper = orderTimelineMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.timelineRecorder = timelineRecorder;
        this.releaseService = releaseService;
        this.settleService = settleService;
        this.notifyService = notifyService;
        this.configService = configService;
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
        try {
            for (HelpOrder order : orders) {
                if (releaseService.releaseByTimeout(order)) {
                    moved++;
                }
            }
        } catch (Exception e) {
            log.warn("J2 执行异常：{}", e.getMessage());
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
        try {
            for (HelpOrder order : orders) {
                if (settleService.settle(order, 0L)) {
                    moved++;
                }
            }
        } catch (Exception e) {
            log.warn("J3 执行异常：{}", e.getMessage());
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
        int hits = 0;
        List<User> users = userMapper.selectList(null);
        for (User user : users) {
            if (!"NORMAL".equals(user.getStatus())) {
                continue;
            }
            // 滥用急标：7 天内标急的求助中，被主动取消或无人接单超时的数量达阈值
            Long urgentAbuse = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                    .eq(HelpOrder::getRequesterId, user.getId())
                    .eq(HelpOrder::getUrgentFlag, 1)
                    .ge(HelpOrder::getCreatedAt, since)
                    .in(HelpOrder::getStatus, OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name()));
            if (urgentAbuse != null
                    && urgentAbuse >= configService.getInt("abuse.urgent_count_7d", 2)
                    && !deductedRecently(user.getId(), "URGENT_ABUSE", since)) {
                deduct(user.getId(), "URGENT_ABUSE", configService.getInt("credit.urgent_abuse", -10));
                hits++;
            }
            // 恶意取消：7 天内主动取消自己发布的求助数量达阈值
            Long cancelCount = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                    .eq(HelpOrder::getRequesterId, user.getId())
                    .eq(HelpOrder::getCancelBy, "REQUESTER")
                    .ge(HelpOrder::getCancelledAt, since));
            if (cancelCount != null
                    && cancelCount >= configService.getInt("abuse.cancel_count_7d", 3)
                    && !deductedRecently(user.getId(), "MALICIOUS_CANCEL", since)) {
                deduct(user.getId(), "MALICIOUS_CANCEL", configService.getInt("credit.malicious_cancel", -10));
                hits++;
            }
        }
        log.info("J6 滥用判定：命中 {} 次", hits);
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

    private boolean deductedRecently(Long uid, String reasonCode, LocalDateTime since) {
        Long count = creditLedgerMapper.selectCount(Wrappers.<CreditLedger>lambdaQuery()
                .eq(CreditLedger::getUserId, uid)
                .eq(CreditLedger::getReasonCode, reasonCode)
                .ge(CreditLedger::getCreatedAt, since));
        return count != null && count > 0;
    }

    private void deduct(Long uid, String reasonCode, int delta) {
        User user = userMapper.selectById(uid);
        int balance = user == null || user.getCreditScore() == null ? 0 : user.getCreditScore();
        int newBalance = Math.max(0, balance + delta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getCreditScore, newBalance));
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(uid);
        ledger.setDelta(delta);
        ledger.setReasonCode(reasonCode);
        ledger.setBalanceAfter(newBalance);
        ledger.setOperatorId(0L);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
        notifyService.creditChanged(uid);
    }
}