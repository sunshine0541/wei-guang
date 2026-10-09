package com.weiguang.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.enums.OrderType;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.appeal.entity.Appeal;
import com.weiguang.modules.appeal.mapper.AppealMapper;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.entity.PointLedger;
import com.weiguang.modules.incentive.entity.StoryCard;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.incentive.mapper.PointLedgerMapper;
import com.weiguang.modules.incentive.mapper.StoryCardMapper;
import com.weiguang.modules.light.entity.LightEvent;
import com.weiguang.modules.light.mapper.LightEventMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.ConfirmRequest;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 双确认与结算（详设 §3.3 O11/O12、§4.1 #10–#13）：
 * 标记完成 → 另一方确认（结算：微光值/积分/点亮/徽记/故事卡）或拒绝（进入申诉中）。
 * settle() 供 T10 的 J3 超时默认通过与 T11 仲裁复用。
 */
@Slf4j
@Service
public class SettleService {

    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final PointLedgerMapper pointLedgerMapper;
    private final StoryCardMapper storyCardMapper;
    private final LightEventMapper lightEventMapper;
    private final AppealMapper appealMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final NotifyService notifyService;
    private final ConfigService configService;

    public SettleService(HelpOrderMapper helpOrderMapper,
                         UserMapper userMapper,
                         UserProfileMapper userProfileMapper,
                         ChatSessionMapper chatSessionMapper,
                         CreditLedgerMapper creditLedgerMapper,
                         PointLedgerMapper pointLedgerMapper,
                         StoryCardMapper storyCardMapper,
                         LightEventMapper lightEventMapper,
                         AppealMapper appealMapper,
                         OrderTimelineRecorder timelineRecorder,
                         NotifyService notifyService,
                         ConfigService configService) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.pointLedgerMapper = pointLedgerMapper;
        this.storyCardMapper = storyCardMapper;
        this.lightEventMapper = lightEventMapper;
        this.appealMapper = appealMapper;
        this.timelineRecorder = timelineRecorder;
        this.notifyService = notifyService;
        this.configService = configService;
    }

    /** O11 标记完成：双方任一方，IN_SERVICE → PENDING_CONFIRM */
    @Transactional
    public void complete(Long uid, Long orderId) {
        HelpOrder order = requireOrder(orderId);
        requireParticipant(order, uid);
        boolean iAmRequester = uid.equals(order.getRequesterId());
        LocalDateTime now = LocalDateTime.now();
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, OrderStatus.IN_SERVICE.name())
                .set(HelpOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .set(HelpOrder::getConfirmInitiator, iAmRequester ? "REQUESTER" : "HELPER")
                .set(HelpOrder::getConfirmDeadline,
                        now.plusHours(configService.getInt("order.confirm_timeout_hours", 24))));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        timelineRecorder.record(orderId, "COMPLETE_REQUEST", uid, null);
        Long peerId = iAmRequester ? order.getHelperId() : order.getRequesterId();
        notifyService.confirmRequest(peerId, nicknameOf(uid), order.getTitle(), orderId);
    }

    /** O12 确认/拒绝：仅另一方；agree=true → 结算；agree=false → APPEALING + appeal(CONFIRM_REJECT) */
    @Transactional
    public void confirm(Long uid, Long orderId, ConfirmRequest request) {
        HelpOrder order = requireOrder(orderId);
        requireParticipant(order, uid);
        if (!OrderStatus.PENDING_CONFIRM.name().equals(order.getStatus())) {
            // 幂等冲突（重复结算）按 40003；其余状态不允许按 40002
            throw new BizException(OrderStatus.COMPLETED.name().equals(order.getStatus())
                    ? ErrorCode.DUPLICATE_SUBMIT : ErrorCode.STATE_NOT_ALLOWED);
        }
        // 仅"另一方"可确认/拒绝（标记完成的一方不能自己确认）
        boolean iAmRequester = uid.equals(order.getRequesterId());
        boolean initiatorIsRequester = "REQUESTER".equals(order.getConfirmInitiator());
        if (request.getAgree() == null || iAmRequester == initiatorIsRequester) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        if (Boolean.TRUE.equals(request.getAgree())) {
            if (!settle(order, uid)) {
                throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
            }
            return;
        }

        // 拒绝确认：理由必填 ≤200
        if (request.getReason() == null || request.getReason().isBlank()
                || request.getReason().length() > 200) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .set(HelpOrder::getStatus, OrderStatus.APPEALING.name())
                .set(HelpOrder::getPreAppealStatus, OrderStatus.PENDING_CONFIRM.name()));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        Appeal appeal = new Appeal();
        appeal.setOrderId(orderId);
        appeal.setInitiatorId(uid);
        appeal.setType("CONFIRM_REJECT");
        appeal.setReason(request.getReason());
        appeal.setStatus("PENDING");
        appeal.setCreatedAt(LocalDateTime.now());
        appealMapper.insert(appeal);
        timelineRecorder.record(orderId, "APPEAL_OPEN", uid, request.getReason());
    }

    /**
     * 结算（详设 §3.3 O12 agree=true 分支，全部动作同事务）：
     * 状态落 COMPLETED + 助人者微光值 + 积分（unique_key 幂等 + 同对用户自然周上限）
     * + 受助者 helped_before/点亮 + 助人者徽记 + 故事卡 + 释放计数清零 + 互动时间 + 会话关闭 + 通知。
     * 返回 false = 条件更新未命中（已结算/非待确认），由调用方决定错误码。
     */
    @Transactional
    public boolean settle(HelpOrder order, Long operatorId) {
        LocalDateTime now = LocalDateTime.now();
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                .set(HelpOrder::getStatus, OrderStatus.COMPLETED.name())
                .set(HelpOrder::getCompletedAt, now)
                .set(HelpOrder::getAppealDeadline,
                        now.plusDays(configService.getInt("order.appeal_window_days", 7))));
        if (rows == 0) {
            return false;
        }

        Long requesterId = order.getRequesterId();
        Long helperId = order.getHelperId();
        User requester = userMapper.selectById(requesterId);
        User helper = userMapper.selectById(helperId);

        // 1) 助人者微光值 +credit.complete（下限 0 收敛）
        int creditDelta = configService.getInt("credit.complete", 2);
        int helperCredit = helper == null || helper.getCreditScore() == null ? 0 : helper.getCreditScore();
        int newCredit = Math.max(0, helperCredit + creditDelta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, helperId)
                .set(User::getCreditScore, newCredit));
        CreditLedger creditLedger = new CreditLedger();
        creditLedger.setUserId(helperId);
        creditLedger.setDelta(creditDelta);
        creditLedger.setReasonCode("COMPLETE");
        creditLedger.setOrderId(order.getId());
        creditLedger.setBalanceAfter(newCredit);
        creditLedger.setOperatorId(0L);
        creditLedger.setCreatedAt(now);
        creditLedgerMapper.insert(creditLedger);
        notifyService.creditChanged(helperId);

        // 2) 积分：同对用户（不区分方向）本自然周已结算 <points.weekly_pair_limit 才发放；
        //    unique_key=ORDER_COMPLETE_<orderId> 冲突即跳过（幂等）
        int weeklyLimit = configService.getInt("points.weekly_pair_limit", 3);
        LocalDateTime weekStart = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        Long pairCount = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.COMPLETED.name())
                .ge(HelpOrder::getCompletedAt, weekStart)
                .and(outer -> outer
                        .and(inner -> inner.eq(HelpOrder::getRequesterId, requesterId)
                                .eq(HelpOrder::getHelperId, helperId))
                        .or(inner -> inner.eq(HelpOrder::getRequesterId, helperId)
                                .eq(HelpOrder::getHelperId, requesterId))));
        if (pairCount != null && pairCount <= weeklyLimit) {
            int perComplete = configService.getInt("points.per_complete", 10);
            try {
                PointLedger pointLedger = new PointLedger();
                pointLedger.setUserId(helperId);
                pointLedger.setDelta(perComplete);
                pointLedger.setReasonCode("COMPLETE");
                pointLedger.setOrderId(order.getId());
                pointLedger.setUniqueKey("ORDER_COMPLETE_" + order.getId());
                pointLedger.setBalanceAfter(latestPointBalance(helperId) + perComplete);
                pointLedger.setCreatedAt(now);
                pointLedgerMapper.insert(pointLedger);
                notifyService.pointsCredited(helperId, perComplete);
            } catch (DuplicateKeyException e) {
                log.info("积分已发放过，跳过：order={}", order.getId());
            }
        }

        // 3) 受助者 helped_before=1；UNLIT/RESTING → STEADY（清空 rest_since/light_prev_state）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, requesterId)
                .set(User::getHelpedBefore, 1));
        String requesterLight = requester == null ? null : requester.getLightState();
        if ("UNLIT".equals(requesterLight) || "RESTING".equals(requesterLight)) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, requesterId)
                    .eq(User::getLightState, requesterLight)
                    .set(User::getLightState, "STEADY")
                    .set(User::getRestSince, null)
                    .set(User::getLightPrevState, null));
            recordLightEvent("STATE_CHANGE", requesterId, requesterLight, "STEADY", null, order.getId(), now);
        }

        // 4) 助人者徽记：曾被帮助过（helped_before=1）且当前常亮且未获得过 → LIGHT_PASSED
        if (helper != null
                && Integer.valueOf(1).equals(helper.getHelpedBefore())
                && "STEADY".equals(helper.getLightState())
                && (helper.getLightPassed() == null || helper.getLightPassed() == 0)) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, helperId)
                    .set(User::getLightPassed, 1));
            recordLightEvent("BADGE_GAIN", helperId, null, null, "LIGHT_PASSED", order.getId(), now);
        }

        // 5) 故事卡（本地模板；order 唯一键冲突即跳过）
        try {
            StoryCard card = new StoryCard();
            card.setOrderId(order.getId());
            card.setRequesterName(nicknameOf(requesterId));
            card.setHelperName(nicknameOf(helperId));
            card.setThing(order.getTitle());
            card.setSummary(summaryByType(order.getOrderType()));
            card.setCreatedAt(now);
            storyCardMapper.insert(card);
        } catch (DuplicateKeyException e) {
            log.info("故事卡已生成过，跳过：order={}", order.getId());
        }

        // 6) 求助者连续被释放计数清零
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, requesterId)
                .set(User::getConsecutiveReleaseCount, 0));

        // 7) 双方互动时间
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, requesterId).set(User::getLastInteractionAt, now));
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, helperId).set(User::getLastInteractionAt, now));

        // 8) 关闭当前轮会话
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound())
                .eq(ChatSession::getStatus, "ACTIVE")
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getClosedAt, now));

        // 9) 时间线 + 双方 SETTLED 通知
        timelineRecorder.record(order.getId(), "CONFIRM", operatorId, null);
        timelineRecorder.record(order.getId(), "COMPLETE", 0L, null);
        notifyService.settled(requesterId, order.getTitle(), order.getId());
        notifyService.settled(helperId, order.getTitle(), order.getId());
        return true;
    }

    /** 故事卡一句话小结（详设 P12 写死三套模板之一） */
    public static String summaryByType(String orderType) {
        if (OrderType.REMOTE.name().equals(orderType)) {
            return "隔着屏幕，温暖也准时到达。";
        }
        if (OrderType.ACCOMPANY.name().equals(orderType)) {
            return "一路同行，一路话家常。";
        }
        return "一次敲门，一份安心。";
    }

    private int latestPointBalance(Long uid) {
        PointLedger latest = pointLedgerMapper.selectOne(Wrappers.<PointLedger>lambdaQuery()
                .eq(PointLedger::getUserId, uid)
                .orderByDesc(PointLedger::getId)
                .last("LIMIT 1"));
        return latest == null || latest.getBalanceAfter() == null ? 0 : latest.getBalanceAfter();
    }

    private void recordLightEvent(String eventType, Long uid, String fromState, String toState,
                                  String badge, Long orderId, LocalDateTime now) {
        LightEvent event = new LightEvent();
        event.setUserId(uid);
        event.setEventType(eventType);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setBadge(badge);
        event.setOrderId(orderId);
        event.setCreatedAt(now);
        lightEventMapper.insert(event);
    }

    private HelpOrder requireOrder(Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return order;
    }

    private void requireParticipant(HelpOrder order, Long uid) {
        if (!uid.equals(order.getRequesterId()) && !uid.equals(order.getHelperId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private String nicknameOf(Long uid) {
        UserProfile profile = userProfileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, uid));
        return profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname();
    }
}