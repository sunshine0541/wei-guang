package com.weiguang.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.ReleaseRequest;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.entity.ReleaseRecord;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.mapper.ReleaseRecordMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * O8 释放（放弃/实在帮不上/服务中退出）与 J2 确认开始超时（详设 §3.3 O8、§4.1 #4–#7）。
 * 三条释放路径共用 {@link #coreRelease}；同时被定时任务复用。
 */
@Slf4j
@Service
public class ReleaseService {

    private static final String REASON_QUIT = "HELPER_QUIT";
    private static final String REASON_HELPLESS = "HELPLESS";
    private static final String REASON_LOST = "HELPER_LOST";
    /** J2 定时任务用的原因类型 */
    public static final String REASON_START_TIMEOUT = "START_TIMEOUT";

    private static final String PENALTY_NONE = "NONE";
    private static final String PENALTY_MINUS5 = "MINUS5";
    private static final String PENALTY_MINUS15 = "MINUS15";

    private static final Set<String> RELEASABLE_STATES =
            Set.of(OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name());

    private final HelpOrderMapper helpOrderMapper;
    private final ReleaseRecordMapper releaseRecordMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ConfigService configService;
    private final NotifyService notifyService;
    private final OrderTimelineRecorder timelineRecorder;
    private final WsSessionRegistry wsSessionRegistry;

    public ReleaseService(HelpOrderMapper helpOrderMapper,
                          ReleaseRecordMapper releaseRecordMapper,
                          CreditLedgerMapper creditLedgerMapper,
                          ChatSessionMapper chatSessionMapper,
                          UserMapper userMapper,
                          UserProfileMapper userProfileMapper,
                          ConfigService configService,
                          NotifyService notifyService,
                          OrderTimelineRecorder timelineRecorder,
                          WsSessionRegistry wsSessionRegistry) {
        this.helpOrderMapper = helpOrderMapper;
        this.releaseRecordMapper = releaseRecordMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.configService = configService;
        this.notifyService = notifyService;
        this.timelineRecorder = timelineRecorder;
        this.wsSessionRegistry = wsSessionRegistry;
    }

    /** O8 释放：仅当前轮接单者 */
    @Transactional
    public void release(Long uid, Long orderId, ReleaseRequest request) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!uid.equals(order.getHelperId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String reasonType = request == null ? null : request.getReasonType();
        if (reasonType == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        boolean accepted = OrderStatus.ACCEPTED.name().equals(order.getStatus());
        boolean inService = OrderStatus.IN_SERVICE.name().equals(order.getStatus());
        if (!accepted && !inService) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        String penalty;
        String reasonText = request.getReasonText();
        switch (reasonType) {
            case REASON_QUIT -> {
                // 仅 ACCEPTED 态；二次确认由前端执行
                if (!accepted) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                penalty = PENALTY_MINUS5;
            }
            case REASON_HELPLESS -> {
                if (!inService) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                if (reasonText == null || reasonText.isBlank() || reasonText.length() > 200) {
                    throw new BizException(ErrorCode.PARAM_INVALID);
                }
                penalty = withinHelplessFreeWindow(uid) ? PENALTY_NONE : PENALTY_MINUS5;
            }
            case REASON_LOST -> {
                if (!inService) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                penalty = PENALTY_MINUS15;
            }
            default -> throw new BizException(ErrorCode.PARAM_INVALID);
        }

        if (!coreRelease(order, reasonType, uid, reasonText, penalty)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
    }

    /**
     * J2 确认开始超时：释放重派（START_TIMEOUT，−15，operator=0 系统）——详设 §4.1 #4。
     */
    @Transactional
    public boolean releaseByTimeout(HelpOrder order) {
        return coreRelease(order, REASON_START_TIMEOUT, 0L, null, PENALTY_MINUS15);
    }

    /**
     * 释放共用函数（详设 §3.3 O8）：条件更新回 PENDING + round+1 + 重算待响应截止；
     * release_record、关闭会话、时间线(RELEASE)、扣分与违约计数、达阈值冷却、求助者连续被释放计数、通知、WS 推送。
     */
    @Transactional
    public boolean coreRelease(HelpOrder order, String reasonType, Long operatorId, String reasonText, String penalty) {
        LocalDateTime now = LocalDateTime.now();
        int round = order.getCurrentRound() == null ? 1 : order.getCurrentRound();
        int releaseCount = order.getReleaseCount() == null ? 0 : order.getReleaseCount();
        // 释放重派同样按「希望什么时候」重算待响应截止（与 O2 同一策略）
        LocalDateTime nextPublishDeadline = PublishDeadlinePolicy.resolve(
                order.getTimeExpect(),
                Integer.valueOf(1).equals(order.getUrgentFlag()) ? 1 : 0,
                configService.getInt("order.pending_timeout_urgent_hours", 6),
                configService.getInt("order.pending_timeout_hours", 24),
                configService.getInt("order.zombie_clean_days", 7));

        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, order.getStatus())
                .eq(HelpOrder::getHelperId, order.getHelperId())
                .eq(HelpOrder::getVersion, order.getVersion())
                .set(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .set(HelpOrder::getHelperId, null)
                .set(HelpOrder::getAcceptedAt, null)
                .set(HelpOrder::getStartDeadline, null)
                .set(HelpOrder::getStartedAt, null)
                .set(HelpOrder::getCancelPendingBy, null)
                .set(HelpOrder::getCancelPendingDeadline, null)
                .set(HelpOrder::getReleaseCount, releaseCount + 1)
                .set(HelpOrder::getCurrentRound, round + 1)
                .set(HelpOrder::getPublishDeadline, nextPublishDeadline)
                .set(HelpOrder::getVersion, order.getVersion() + 1));
        if (rows == 0) {
            return false;
        }

        ReleaseRecord record = new ReleaseRecord();
        record.setOrderId(order.getId());
        record.setRound(round);
        record.setFromStatus(order.getStatus());
        record.setReasonType(reasonType);
        record.setOperatorId(operatorId == null ? 0L : operatorId);
        record.setReasonText(reasonText);
        record.setPenalty(penalty);
        record.setCreatedAt(now);
        releaseRecordMapper.insert(record);

        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, round)
                .eq(ChatSession::getStatus, "ACTIVE")
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getClosedAt, now));

        timelineRecorder.record(order.getId(), "RELEASE", operatorId, releaseRemark(reasonType, reasonText));

        Long helperId = order.getHelperId();
        if (helperId != null && !PENALTY_NONE.equals(penalty)) {
            applyViolation(helperId, order, reasonType, penalty);
        }

        // 求助者连续被释放计数 +1（v1.3 起只影响"小憩"展示标签，不改光语层级）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, order.getRequesterId())
                .setSql("consecutive_release_count = consecutive_release_count + 1"));

        notifyService.released(order.getRequesterId(), order.getTitle(), order.getId());
        pushOrderStatus(order.getId(), OrderStatus.PENDING.name(), order.getTitle(),
                order.getRequesterId(), helperId);
        return true;
    }

    /** 时间线展示文案：优先用户填写的理由，否则按原因类型给中文（不把枚举裸露给用户） */
    private String releaseRemark(String reasonType, String reasonText) {
        if (reasonText != null && !reasonText.isBlank()) {
            return reasonText;
        }
        return switch (reasonType == null ? "" : reasonType) {
            case REASON_QUIT -> "主动放弃，已重新寻找邻居";
            case REASON_HELPLESS -> "实在帮不上，已重新寻找邻居";
            case REASON_LOST -> "中途退出，已重新寻找邻居";
            case REASON_START_TIMEOUT -> "超时未开始，已重新寻找邻居";
            case "ACCOUNT_CANCELLED" -> "对方账号已注销，已重新寻找邻居";
            default -> "已重新寻找邻居";
        };
    }

    /** 近 7 天免罚释放次数 < order.helpless_release_limit（以 release_record 统计） */
    private boolean withinHelplessFreeWindow(Long helperId) {
        int limit = configService.getInt("order.helpless_release_limit", 3);
        Long used = releaseRecordMapper.selectCount(Wrappers.<ReleaseRecord>lambdaQuery()
                .eq(ReleaseRecord::getReasonType, REASON_HELPLESS)
                .eq(ReleaseRecord::getOperatorId, helperId)
                .eq(ReleaseRecord::getPenalty, PENALTY_NONE)
                .ge(ReleaseRecord::getCreatedAt, LocalDateTime.now().minusDays(7)));
        return (used == null ? 0 : used) < limit;
    }

    /** 扣微光值 + violation_count+1；达阈值 → cooldown_until + COOLDOWN 通知 */
    private void applyViolation(Long helperId, HelpOrder order, String reasonType, String penalty) {
        int delta = PENALTY_MINUS15.equals(penalty)
                ? configService.getInt("credit.start_timeout", -15)
                : configService.getInt("credit.accept_cancel", -5);
        String reasonCode = PENALTY_MINUS15.equals(penalty) ? "START_TIMEOUT" : "ACCEPT_CANCEL";
        if (REASON_LOST.equals(reasonType)) {
            delta = configService.getInt("credit.mid_quit", -15);
            reasonCode = "MID_QUIT";
        }

        User helper = userMapper.selectById(helperId);
        int balance = helper == null || helper.getCreditScore() == null ? 0 : helper.getCreditScore();
        int newBalance = Math.max(0, balance + delta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, helperId)
                .set(User::getCreditScore, newBalance)
                .setSql("violation_count = violation_count + 1"));

        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(helperId);
        ledger.setDelta(delta);
        ledger.setReasonCode(reasonCode);
        ledger.setOrderId(order.getId());
        ledger.setBalanceAfter(newBalance);
        ledger.setOperatorId(0L);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
        notifyService.creditChanged(helperId);

        User refreshed = userMapper.selectById(helperId);
        int threshold = configService.getInt("order.violation_threshold", 2);
        if (refreshed != null && refreshed.getViolationCount() != null
                && refreshed.getViolationCount() >= threshold) {
            int hours = configService.getInt("order.violation_cooldown_hours", 48);
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, helperId)
                    .set(User::getCooldownUntil, LocalDateTime.now().plusHours(hours)));
            notifyService.cooldown(helperId, hours);
        }
    }

    private void pushOrderStatus(Long orderId, String status, String title, Long requesterId, Long helperId) {
        Object envelope = WsEnvelope.orderStatus(orderId, status, title);
        wsSessionRegistry.push(requesterId, envelope);
        if (helperId != null) {
            wsSessionRegistry.push(helperId, envelope);
        }
    }

    /** 接单者昵称（供各流转通知复用） */
    String nicknameOf(Long uid) {
        if (uid == null) {
            return "邻居";
        }
        UserProfile profile = userProfileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, uid));
        return profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname();
    }
}