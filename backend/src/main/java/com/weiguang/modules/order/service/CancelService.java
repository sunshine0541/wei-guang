package com.weiguang.modules.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.CancelConfirmRequest;
import com.weiguang.modules.order.dto.CancelRequest;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * O9 取消（求助者单方取消 / 发起协商取消）与 O10 协商取消应答（详设 §3.3 O9/O10、§4.1 #8–#9）。
 * `mode` 参数区分两种意图，见《实现偏差清单》D13。
 */
@Service
public class CancelService {

    private static final String MODE_CANCEL = "CANCEL";
    private static final String MODE_NEGOTIATE = "NEGOTIATE";
    private static final int REASON_MAX_LENGTH = 200;

    /** 求助者单方取消允许的状态 */
    private static final Set<String> REQUESTER_CANCEL_STATES = Set.of(
            OrderStatus.PENDING.name(), OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name());

    private final HelpOrderMapper helpOrderMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final ConfigService configService;
    private final WsSessionRegistry wsSessionRegistry;
    private final NotifyService notifyService;
    private final UserProfileMapper userProfileMapper;

    public CancelService(HelpOrderMapper helpOrderMapper,
                         ChatSessionMapper chatSessionMapper,
                         OrderTimelineRecorder timelineRecorder,
                         ConfigService configService,
                         WsSessionRegistry wsSessionRegistry,
                         NotifyService notifyService,
                         UserProfileMapper userProfileMapper) {
        this.helpOrderMapper = helpOrderMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.timelineRecorder = timelineRecorder;
        this.configService = configService;
        this.wsSessionRegistry = wsSessionRegistry;
        this.notifyService = notifyService;
        this.userProfileMapper = userProfileMapper;
    }

    /** O9 取消：mode=CANCEL 单方取消（仅求助者）/ mode=NEGOTIATE 发起协商取消（双方，仅服务中） */
    @Transactional
    public void cancel(Long uid, Long orderId, CancelRequest request) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean isRequester = uid.equals(order.getRequesterId());
        boolean isHelper = uid.equals(order.getHelperId());
        if (!isRequester && !isHelper) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String reason = request == null ? null : request.getReason();
        if (reason == null || reason.isBlank() || reason.length() > REASON_MAX_LENGTH) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String mode = request.getMode() == null || request.getMode().isBlank() ? MODE_CANCEL : request.getMode();
        if (!MODE_CANCEL.equals(mode) && !MODE_NEGOTIATE.equals(mode)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }

        if (MODE_NEGOTIATE.equals(mode)) {
            negotiate(uid, order, reason);
            return;
        }
        // 助人者不允许单方取消（详设 §3.3 O9）
        if (!isRequester) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        cancelByRequester(uid, order, reason);
    }

    /** 求助者单方取消：PENDING/ACCEPTED/IN_SERVICE → CANCELLED */
    private void cancelByRequester(Long uid, HelpOrder order, String reason) {
        if (!REQUESTER_CANCEL_STATES.contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now();
        // PENDING 撤回时没有对方，不开放申诉窗口（D53）
        LocalDateTime appealDeadline = OrderStatus.PENDING.name().equals(order.getStatus())
                ? null : now.plusDays(configService.getInt("order.appeal_window_days", 7));
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, order.getStatus())
                .set(HelpOrder::getStatus, OrderStatus.CANCELLED.name())
                .set(HelpOrder::getCancelBy, "REQUESTER")
                .set(HelpOrder::getCancelReason, reason)
                .set(HelpOrder::getCancelledAt, now)
                .set(HelpOrder::getAppealDeadline, appealDeadline)
                .set(HelpOrder::getCancelPendingBy, null)
                .set(HelpOrder::getCancelPendingDeadline, null));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        closeSession(order, now);
        timelineRecorder.record(order.getId(), "CANCEL", uid, reason);
        Long helperId = order.getHelperId();
        if (helperId != null && !helperId.equals(uid)) {
            notifyService.cancelNotice(helperId, order.getTitle(), order.getId());
        }
        push(order, OrderStatus.CANCELLED.name());
    }

    /** 发起协商取消：仅服务中；cancel_pending_by + 24h 应答截止；通知对方（详设 §15.6 CANCEL_REQUEST） */
    private void negotiate(Long uid, HelpOrder order, String reason) {
        if (!OrderStatus.IN_SERVICE.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (order.getCancelPendingBy() != null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        String by = uid.equals(order.getRequesterId()) ? "REQUESTER" : "HELPER";
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, OrderStatus.IN_SERVICE.name())
                .isNull(HelpOrder::getCancelPendingBy)
                .set(HelpOrder::getCancelPendingBy, by)
                .set(HelpOrder::getCancelPendingDeadline,
                        LocalDateTime.now().plusHours(configService.getInt("order.cancel_confirm_timeout_hours", 24)))
                .set(HelpOrder::getCancelReason, reason));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        timelineRecorder.record(order.getId(), "CANCEL_REQUEST", uid, reason);
        Long peerId = uid.equals(order.getRequesterId()) ? order.getHelperId() : order.getRequesterId();
        if (peerId != null) {
            notifyService.cancelRequest(peerId, nicknameOf(uid), order.getTitle(), order.getId());
        }
    }

    /** O10 协商取消应答：仅另一方；agree → CANCELLED；否则清空挂起并记时间线 */
    @Transactional
    public void cancelConfirm(Long uid, Long orderId, CancelConfirmRequest request) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean isRequester = uid.equals(order.getRequesterId());
        boolean isHelper = uid.equals(order.getHelperId());
        if (!isRequester && !isHelper) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String pendingBy = order.getCancelPendingBy();
        if (pendingBy == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        // 仅"另一方"可应答
        boolean iAmInitiator = (isRequester && "REQUESTER".equals(pendingBy))
                || (isHelper && "HELPER".equals(pendingBy));
        if (iAmInitiator) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (request == null || request.getAgree() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }

        LocalDateTime now = LocalDateTime.now();
        if (Boolean.TRUE.equals(request.getAgree())) {
            int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getId, orderId)
                    .notIn(HelpOrder::getStatus, OrderStatus.COMPLETED.name(),
                            OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name())
                    .isNotNull(HelpOrder::getCancelPendingBy)
                    .set(HelpOrder::getStatus, OrderStatus.CANCELLED.name())
                    .set(HelpOrder::getCancelBy, pendingBy)
                    .set(HelpOrder::getCancelledAt, now)
                    .set(HelpOrder::getAppealDeadline,
                            now.plusDays(configService.getInt("order.appeal_window_days", 7)))
                    .set(HelpOrder::getCancelPendingBy, null)
                    .set(HelpOrder::getCancelPendingDeadline, null));
            if (rows == 0) {
                throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
            }
            closeSession(order, now);
            timelineRecorder.record(orderId, "CANCEL", uid, "协商取消同意");
            push(order, OrderStatus.CANCELLED.name());
            return;
        }

        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .notIn(HelpOrder::getStatus, OrderStatus.COMPLETED.name(),
                        OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name())
                .isNotNull(HelpOrder::getCancelPendingBy)
                .set(HelpOrder::getCancelPendingBy, null)
                .set(HelpOrder::getCancelPendingDeadline, null));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        timelineRecorder.record(orderId, "CANCEL_CONFIRM_REJECT", uid, "协商取消被拒绝");
        Long initiatorId = "REQUESTER".equals(pendingBy) ? order.getRequesterId() : order.getHelperId();
        if (initiatorId != null) {
            notifyService.cancelRejected(initiatorId, order.getTitle(), orderId);
        }
    }

    private void closeSession(HelpOrder order, LocalDateTime now) {
        int round = order.getCurrentRound() == null ? 1 : order.getCurrentRound();
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, round)
                .eq(ChatSession::getStatus, "ACTIVE")
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getClosedAt, now));
    }

    private void push(HelpOrder order, String status) {
        Object envelope = WsEnvelope.orderStatus(order.getId(), status, order.getTitle());
        if (order.getRequesterId() != null) {
            wsSessionRegistry.push(order.getRequesterId(), envelope);
        }
        if (order.getHelperId() != null) {
            wsSessionRegistry.push(order.getHelperId(), envelope);
        }
    }

    private String nicknameOf(Long uid) {
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, uid)
                .last("LIMIT 1"));
        return profile == null || profile.getNickname() == null || profile.getNickname().isBlank()
                ? "邻居" : profile.getNickname();
    }
}