package com.weiguang.modules.appeal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.appeal.dto.AdminAppealDetailVO;
import com.weiguang.modules.appeal.dto.AdminAppealVO;
import com.weiguang.modules.appeal.dto.AppealCreateRequest;
import com.weiguang.modules.appeal.dto.AppealVO;
import com.weiguang.modules.appeal.dto.ArbitrateRequest;
import com.weiguang.modules.appeal.dto.FeedbackCreateRequest;
import com.weiguang.modules.appeal.entity.Appeal;
import com.weiguang.modules.appeal.mapper.AppealMapper;
import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.entity.PointLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.incentive.mapper.PointLedgerMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.TimelineVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.entity.OrderTimeline;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.mapper.OrderTimelineMapper;
import com.weiguang.modules.order.service.OrderTimelineRecorder;
import com.weiguang.modules.order.service.SettleService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 申诉与最小闭环（详设 §2.19、§3.4、§3.6 F1–F3）：
 * F1 提交申诉（范围校验）/ F2 反馈 / F3 我的列表；管理端列表、详情与仲裁（落回 + 补发/追回 + 败诉处置）。
 */
@Slf4j
@Service
public class AppealService {

    private static final String TYPE_CONFIRM_REJECT = "CONFIRM_REJECT";
    private static final String TYPE_POST_ORDER = "POST_ORDER";
    private static final String TYPE_FEEDBACK = "FEEDBACK";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String RESULT_TO_COMPLETED = "TO_COMPLETED";
    private static final String RESULT_TO_CANCELLED = "TO_CANCELLED";
    private static final int REASON_MAX = 200;
    private static final int FEEDBACK_MAX = 500;
    private static final int CONCLUSION_MAX = 255;
    /**
     * 时间线中的管理员操作者标记：operator_id=Long.MAX_VALUE 恒派生为 ADMIN。
     * 不写真实 admin_user.id——管理员与用户 id 分属两套序列，数值可能碰撞而被派生为 REQUESTER/HELPER；
     * 也不能用 -1（order_timeline.operator_id 为 UNSIGNED）。见《实现偏差清单》D19。
     */
    private static final long ADMIN_TIMELINE_OPERATOR = Long.MAX_VALUE;

    private final AppealMapper appealMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final PointLedgerMapper pointLedgerMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final OrderTimelineMapper orderTimelineMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final NotifyService notifyService;
    private final ConfigService configService;
    private final SettleService settleService;
    private final WsSessionRegistry wsSessionRegistry;

    public AppealService(AppealMapper appealMapper,
                         HelpOrderMapper helpOrderMapper,
                         UserMapper userMapper,
                         UserProfileMapper userProfileMapper,
                         CreditLedgerMapper creditLedgerMapper,
                         PointLedgerMapper pointLedgerMapper,
                         ChatSessionMapper chatSessionMapper,
                         ChatMessageMapper chatMessageMapper,
                         OrderTimelineMapper orderTimelineMapper,
                         OrderTimelineRecorder timelineRecorder,
                         NotifyService notifyService,
                         ConfigService configService,
                         SettleService settleService,
                         WsSessionRegistry wsSessionRegistry) {
        this.appealMapper = appealMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.pointLedgerMapper = pointLedgerMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.orderTimelineMapper = orderTimelineMapper;
        this.timelineRecorder = timelineRecorder;
        this.notifyService = notifyService;
        this.configService = configService;
        this.settleService = settleService;
        this.wsSessionRegistry = wsSessionRegistry;
    }

    /** F1 提交申诉（范围：仅 COMPLETED/CANCELLED 且窗口内；封禁用户可提交，拦截器已豁免） */
    @Transactional
    public Long submitAppeal(Long uid, AppealCreateRequest request) {
        if (request.getOrderId() == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String type = request.getType() == null || request.getType().isBlank()
                ? TYPE_POST_ORDER : request.getType();
        if (TYPE_FEEDBACK.equals(type) || TYPE_CONFIRM_REJECT.equals(type)) {
            // 反馈走 F2；待确认拒绝由 O12 自动生成，不经本接口（详设 §3.6 F1）
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (!TYPE_POST_ORDER.equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String reason = request.getReason();
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (reason.length() > REASON_MAX) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }

        HelpOrder order = helpOrderMapper.selectById(request.getOrderId());
        if (order == null || !isParticipant(order, uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = LocalDateTime.now();
        // 同一订单已有进行中申诉 → 40003（优先于范围判定，详设 §3.6 F1 口径）
        Long pending = appealMapper.selectCount(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getOrderId, order.getId())
                .eq(Appeal::getStatus, STATUS_PENDING));
        if (pending != null && pending > 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        boolean inRange = OrderStatus.COMPLETED.name().equals(order.getStatus())
                || OrderStatus.CANCELLED.name().equals(order.getStatus());
        if (!inRange || order.getAppealDeadline() == null || now.isAfter(order.getAppealDeadline())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        Appeal appeal = new Appeal();
        appeal.setOrderId(order.getId());
        appeal.setInitiatorId(uid);
        appeal.setType(TYPE_POST_ORDER);
        appeal.setReason(reason);
        // B：凭证图片（可选，前端先走 /api/upload/image）
        if (request.getEvidenceUrl() != null && !request.getEvidenceUrl().isBlank()) {
            String evidence = request.getEvidenceUrl().trim();
            appeal.setEvidenceUrl(evidence.length() > 255 ? evidence.substring(0, 255) : evidence);
        }
        appeal.setStatus(STATUS_PENDING);
        appeal.setCreatedAt(now);
        appealMapper.insert(appeal);

        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, order.getId())
                .eq(HelpOrder::getStatus, order.getStatus())
                .set(HelpOrder::getStatus, OrderStatus.APPEALING.name())
                .set(HelpOrder::getPreAppealStatus, order.getStatus()));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        timelineRecorder.record(order.getId(), "APPEAL_OPEN", uid, reason);
        pushStatus(order.getRequesterId(), order.getHelperId(), order.getId(),
                OrderStatus.APPEALING.name(), order.getTitle());
        // 注：详设 §3.6 F1 写"事务外通知对方"，但 §4.4 通知文案表无 F1 对应 type 与模板，
        // 按"不得发明文案"原则暂不落通知（见《实现偏差清单》D18）。
        return appeal.getId();
    }

    /** F2 提交反馈（复用 appeal 表，type=FEEDBACK、order_id 为空；封禁用户可提交） */
    @Transactional
    public Long submitFeedback(Long uid, FeedbackCreateRequest request) {
        String content = request.getContent();
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (content.length() > FEEDBACK_MAX) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        Appeal appeal = new Appeal();
        appeal.setInitiatorId(uid);
        appeal.setType(TYPE_FEEDBACK);
        appeal.setReason(content);
        appeal.setStatus(STATUS_PENDING);
        appeal.setCreatedAt(LocalDateTime.now());
        appealMapper.insert(appeal);
        return appeal.getId();
    }

    /** B：撤回本人处理中的申诉或反馈（不删除记录，状态置 WITHDRAWN） */
    @Transactional
    public void withdraw(Long uid, Long appealId) {
        Appeal appeal = appealId == null ? null : appealMapper.selectById(appealId);
        if (appeal == null || !uid.equals(appeal.getInitiatorId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!STATUS_PENDING.equals(appeal.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        int rows = appealMapper.update(null, Wrappers.<Appeal>lambdaUpdate()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getInitiatorId, uid)
                .eq(Appeal::getStatus, STATUS_PENDING)
                .set(Appeal::getStatus, STATUS_WITHDRAWN)
                .set(Appeal::getResolvedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
    }

    /** F3 我的申诉/反馈列表（tab=APPEAL|FEEDBACK） */
    public PageVO<AppealVO> mine(Long uid, String tab, Long page, Long pageSize) {
        String t = tab == null || tab.isBlank() ? "APPEAL" : tab;
        if (!"APPEAL".equals(t) && !"FEEDBACK".equals(t)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        LambdaQueryWrapper<Appeal> wrapper = Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getInitiatorId, uid)
                .orderByDesc(Appeal::getId);
        if ("FEEDBACK".equals(t)) {
            wrapper.eq(Appeal::getType, TYPE_FEEDBACK);
        } else {
            wrapper.ne(Appeal::getType, TYPE_FEEDBACK);
        }
        Page<Appeal> result = appealMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);
        // 关联求助标题（P17 列表展示"针对哪件事"，见 D20）
        java.util.Set<Long> orderIds = result.getRecords().stream()
                .map(Appeal::getOrderId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HelpOrder> orders = orderIds.isEmpty() ? Map.of()
                : helpOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(HelpOrder::getId, Function.identity(), (a, b) -> a));
        return PageVO.of(result.convert(appeal -> {
            AppealVO vo = toVO(appeal);
            HelpOrder order = appeal.getOrderId() == null ? null : orders.get(appeal.getOrderId());
            vo.setOrderTitle(order == null ? null : order.getTitle());
            return vo;
        }));
    }

    /** GET /admin/appeals 申诉/反馈列表（type/status 筛选） */
    public PageVO<AdminAppealVO> adminList(String type, String status, Long page, Long pageSize) {
        if (type != null && !type.isBlank()
                && !TYPE_CONFIRM_REJECT.equals(type) && !TYPE_POST_ORDER.equals(type) && !TYPE_FEEDBACK.equals(type)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (status != null && !status.isBlank()
                && !STATUS_PENDING.equals(status) && !STATUS_RESOLVED.equals(status)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        LambdaQueryWrapper<Appeal> wrapper = Wrappers.<Appeal>lambdaQuery().orderByDesc(Appeal::getId);
        if (type != null && !type.isBlank()) {
            wrapper.eq(Appeal::getType, type);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(Appeal::getStatus, status);
        }
        Page<Appeal> result = appealMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);

        Set<Long> orderIds = result.getRecords().stream()
                .map(Appeal::getOrderId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, HelpOrder> orders = orderIds.isEmpty() ? Map.of()
                : helpOrderMapper.selectBatchIds(orderIds).stream()
                .collect(Collectors.toMap(HelpOrder::getId, Function.identity()));
        Map<Long, UserProfile> profiles = loadProfiles(result.getRecords().stream()
                .map(Appeal::getInitiatorId).toList());

        return PageVO.of(result.convert(appeal -> {
            AdminAppealVO vo = new AdminAppealVO();
            vo.setId(appeal.getId());
            vo.setType(appeal.getType());
            vo.setOrderId(appeal.getOrderId());
            HelpOrder order = appeal.getOrderId() == null ? null : orders.get(appeal.getOrderId());
            vo.setOrderTitle(order == null ? null : order.getTitle());
            vo.setInitiatorId(appeal.getInitiatorId());
            UserProfile profile = profiles.get(appeal.getInitiatorId());
            vo.setInitiatorNickname(profile == null ? null : profile.getNickname());
            vo.setReason(appeal.getReason());
            vo.setStatus(appeal.getStatus());
            vo.setResult(appeal.getResult());
            vo.setConclusion(appeal.getConclusion());
            vo.setCreatedAt(appeal.getCreatedAt());
            vo.setResolvedAt(appeal.getResolvedAt());
            return vo;
        }));
    }

    /** GET /admin/appeals/{id} 详情（申诉含订单/时间线/聊天记录；反馈仅理由与结论） */
    public AdminAppealDetailVO adminDetail(Long appealId) {
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        AdminAppealDetailVO vo = new AdminAppealDetailVO();
        vo.setId(appeal.getId());
        vo.setType(appeal.getType());
        vo.setOrderId(appeal.getOrderId());
        vo.setInitiatorId(appeal.getInitiatorId());
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, appeal.getInitiatorId()));
        vo.setInitiatorNickname(profile == null ? null : profile.getNickname());
        vo.setReason(appeal.getReason());
        vo.setStatus(appeal.getStatus());
        vo.setResult(appeal.getResult());
        vo.setConclusion(appeal.getConclusion());
        vo.setCreatedAt(appeal.getCreatedAt());
        vo.setResolvedAt(appeal.getResolvedAt());

        if (appeal.getOrderId() == null) {
            return vo;
        }
        HelpOrder order = helpOrderMapper.selectById(appeal.getOrderId());
        if (order == null) {
            return vo;
        }
        vo.setOrderTitle(order.getTitle());
        vo.setOrderStatus(order.getStatus());
        vo.setOrderType(order.getOrderType());
        vo.setRequesterId(order.getRequesterId());
        vo.setHelperId(order.getHelperId());

        vo.setTimeline(orderTimelineMapper.selectList(Wrappers.<OrderTimeline>lambdaQuery()
                        .eq(OrderTimeline::getOrderId, order.getId())
                        .orderByAsc(OrderTimeline::getId)).stream()
                .map(record -> {
                    TimelineVO t = new TimelineVO();
                    t.setEventType(record.getEventType());
                    t.setOperatorRole(operatorRole(record.getOperatorId(), order));
                    t.setRemark(record.getRemark());
                    t.setCreatedAt(record.getCreatedAt());
                    return t;
                }).toList());

        ChatSession session = chatSessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound())
                .orderByDesc(ChatSession::getId)
                .last("LIMIT 1"));
        if (session != null) {
            vo.setChatMessages(chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                            .eq(ChatMessage::getSessionId, session.getId())
                            .orderByAsc(ChatMessage::getId)
                            .last("LIMIT 200")).stream()
                    .map(message -> {
                        MessageVO m = new MessageVO();
                        m.setMsgId(message.getId());
                        m.setSenderId(message.getSenderId());
                        m.setMessageType(message.getMessageType());
                        m.setContent(message.getContent());
                        m.setRiskFlag(message.getRiskFlag());
                        m.setCreatedAt(message.getCreatedAt());
                        return m;
                    }).toList());
        }
        return vo;
    }

    /**
     * POST /admin/appeals/{id}/arbitrate 仲裁（详设 §3.4 落回规则）：
     * TO_COMPLETED → 结算未发生则复用 O12 结算函数补发（unique_key 幂等）；
     * TO_CANCELLED → 已结算则追回（point_ledger/credit_ledger 反向明细）；
     * 败诉方 APPEAL_LOSS −20（按方处置）；结论写时间线 ARBITRATE；通知双方 APPEAL_RESULT。
     */
    @Transactional
    public void arbitrate(Long adminId, Long appealId, ArbitrateRequest request) {
        String result = request.getResult();
        if (!RESULT_TO_COMPLETED.equals(result) && !RESULT_TO_CANCELLED.equals(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String conclusion = request.getConclusion();
        if (conclusion == null || conclusion.isBlank() || conclusion.length() > CONCLUSION_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (TYPE_FEEDBACK.equals(appeal.getType())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now();
        int rows = appealMapper.update(null, Wrappers.<Appeal>lambdaUpdate()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getStatus, STATUS_PENDING)
                .set(Appeal::getStatus, STATUS_RESOLVED)
                .set(Appeal::getHandlerId, adminId)
                .set(Appeal::getResult, result)
                .set(Appeal::getConclusion, conclusion)
                .set(Appeal::getResolvedAt, now));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }

        HelpOrder order = helpOrderMapper.selectById(appeal.getOrderId());
        if (order == null || !OrderStatus.APPEALING.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String finalStatus;
        String preAppealStatus = order.getPreAppealStatus();
        if (RESULT_TO_COMPLETED.equals(result)) {
            if (OrderStatus.PENDING_CONFIRM.name().equals(preAppealStatus)) {
                rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                        .eq(HelpOrder::getId, order.getId())
                        .eq(HelpOrder::getStatus, OrderStatus.APPEALING.name())
                        .eq(HelpOrder::getPreAppealStatus, OrderStatus.PENDING_CONFIRM.name())
                        .set(HelpOrder::getStatus, OrderStatus.PENDING_CONFIRM.name())
                        .set(HelpOrder::getPreAppealStatus, null));
                if (rows == 0) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                // 复用 O12 结算函数（状态条件更新 + unique_key 幂等防二次补发）
                if (!settleService.settle(order, ADMIN_TIMELINE_OPERATOR)) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                finalStatus = OrderStatus.COMPLETED.name();
            } else if (OrderStatus.COMPLETED.name().equals(preAppealStatus)) {
                rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                        .eq(HelpOrder::getId, order.getId())
                        .eq(HelpOrder::getStatus, OrderStatus.APPEALING.name())
                        .eq(HelpOrder::getPreAppealStatus, OrderStatus.COMPLETED.name())
                        .set(HelpOrder::getStatus, OrderStatus.COMPLETED.name())
                        .set(HelpOrder::getPreAppealStatus, null));
                if (rows == 0) {
                    throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
                }
                finalStatus = OrderStatus.COMPLETED.name();
            } else {
                // 取消态无结算流程可补发（无接单者或未发生结算）
                throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
            }
        } else {
            rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getId, order.getId())
                    .eq(HelpOrder::getStatus, OrderStatus.APPEALING.name())
                    .set(HelpOrder::getStatus, OrderStatus.CANCELLED.name())
                    .set(HelpOrder::getPreAppealStatus, null)
                    .set(HelpOrder::getCancelledAt, now)
                    .set(HelpOrder::getCancelBy, "ADMIN")
                    .set(HelpOrder::getCancelReason, conclusion)
                    .set(HelpOrder::getAppealDeadline, now));
            if (rows == 0) {
                throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
            }
            revertSettlement(order, request.getRevertCreditIds(), adminId);
            closeActiveSession(order);
            finalStatus = OrderStatus.CANCELLED.name();
        }

        if (request.getPenalties() != null) {
            for (ArbitrateRequest.Penalty penalty : request.getPenalties()) {
                if (penalty.getUserId() == null || !"APPEAL_LOSS".equals(penalty.getReasonCode())
                        || (!penalty.getUserId().equals(order.getRequesterId())
                        && !penalty.getUserId().equals(order.getHelperId()))) {
                    throw new BizException(ErrorCode.PARAM_INVALID);
                }
                applyCreditDelta(penalty.getUserId(), configService.getInt("credit.appeal_loss", -20),
                        "APPEAL_LOSS", order.getId(), adminId);
            }
        }

        timelineRecorder.record(order.getId(), "ARBITRATE", ADMIN_TIMELINE_OPERATOR, conclusion);
        pushStatus(order.getRequesterId(), order.getHelperId(), order.getId(), finalStatus, order.getTitle());
        notifyService.appealResult(order.getRequesterId(), order.getTitle(), appeal.getId());
        if (order.getHelperId() != null) {
            notifyService.appealResult(order.getHelperId(), order.getTitle(), appeal.getId());
        }
        log.info("仲裁完成：appeal={} order={} result={}", appealId, order.getId(), result);
    }

    /** POST /admin/feedbacks/{id}/reply 反馈处理：写结论 → 通知 FEEDBACK_RESULT */
    @Transactional
    public void replyFeedback(Long adminId, Long appealId, String conclusion) {
        if (conclusion == null || conclusion.isBlank() || conclusion.length() > CONCLUSION_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Appeal appeal = appealMapper.selectById(appealId);
        if (appeal == null || !TYPE_FEEDBACK.equals(appeal.getType())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int rows = appealMapper.update(null, Wrappers.<Appeal>lambdaUpdate()
                .eq(Appeal::getId, appealId)
                .eq(Appeal::getStatus, STATUS_PENDING)
                .set(Appeal::getStatus, STATUS_RESOLVED)
                .set(Appeal::getHandlerId, adminId)
                .set(Appeal::getConclusion, conclusion)
                .set(Appeal::getResolvedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        notifyService.feedbackResult(appeal.getInitiatorId());
    }

    /** TO_CANCELLED 追回：积分反向（unique_key 幂等）+ 微光值反向（全部 COMPLETE 明细或指定明细） */
    private void revertSettlement(HelpOrder order, List<Long> revertCreditIds, Long adminId) {
        PointLedger point = pointLedgerMapper.selectOne(Wrappers.<PointLedger>lambdaQuery()
                .eq(PointLedger::getUniqueKey, "ORDER_COMPLETE_" + order.getId()));
        if (point != null) {
            try {
                PointLedger revert = new PointLedger();
                revert.setUserId(point.getUserId());
                revert.setDelta(-point.getDelta());
                revert.setReasonCode("APPEAL_REVERT");
                revert.setOrderId(order.getId());
                revert.setUniqueKey("APPEAL_REVERT_" + order.getId());
                revert.setBalanceAfter(Math.max(0, latestPointBalance(point.getUserId()) - point.getDelta()));
                revert.setCreatedAt(LocalDateTime.now());
                pointLedgerMapper.insert(revert);
            } catch (DuplicateKeyException e) {
                log.info("积分已追回过，跳过：order={}", order.getId());
            }
        }

        List<CreditLedger> targets;
        if (revertCreditIds != null && !revertCreditIds.isEmpty()) {
            targets = creditLedgerMapper.selectBatchIds(revertCreditIds);
            if (targets.size() != revertCreditIds.size()) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            for (CreditLedger target : targets) {
                if (!order.getId().equals(target.getOrderId())) {
                    throw new BizException(ErrorCode.PARAM_INVALID);
                }
            }
        } else {
            targets = creditLedgerMapper.selectList(Wrappers.<CreditLedger>lambdaQuery()
                    .eq(CreditLedger::getOrderId, order.getId())
                    .eq(CreditLedger::getReasonCode, "COMPLETE"));
        }
        for (CreditLedger target : targets) {
            applyCreditDelta(target.getUserId(), -target.getDelta(), "APPEAL_REVERT", order.getId(), adminId);
        }
    }

    /** 微光值变动（下限 0 收敛）+ 明细 + 通知 */
    private void applyCreditDelta(Long userId, int delta, String reasonCode, Long orderId, Long operatorId) {
        User user = userMapper.selectById(userId);
        int current = user == null || user.getCreditScore() == null ? 0 : user.getCreditScore();
        int balance = Math.max(0, current + delta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getCreditScore, balance));
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setReasonCode(reasonCode);
        ledger.setOrderId(orderId);
        ledger.setBalanceAfter(balance);
        ledger.setOperatorId(operatorId);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
        notifyService.creditChanged(userId);
    }

    private void closeActiveSession(HelpOrder order) {
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound())
                .eq(ChatSession::getStatus, "ACTIVE")
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getClosedAt, LocalDateTime.now()));
    }

    private int latestPointBalance(Long uid) {
        PointLedger latest = pointLedgerMapper.selectOne(Wrappers.<PointLedger>lambdaQuery()
                .eq(PointLedger::getUserId, uid)
                .orderByDesc(PointLedger::getId)
                .last("LIMIT 1"));
        return latest == null || latest.getBalanceAfter() == null ? 0 : latest.getBalanceAfter();
    }

    /** 是否可申诉（P07"申诉入口仅在允许范围显示"；O4 的 canAppeal 亦复用本判定） */
    public boolean canAppeal(HelpOrder order, Long uid) {
        if (order == null || !isParticipant(order, uid)) {
            return false;
        }
        boolean inRange = OrderStatus.COMPLETED.name().equals(order.getStatus())
                || OrderStatus.CANCELLED.name().equals(order.getStatus());
        if (!inRange || order.getAppealDeadline() == null
                || LocalDateTime.now().isAfter(order.getAppealDeadline())) {
            return false;
        }
        Long pending = appealMapper.selectCount(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getOrderId, order.getId())
                .eq(Appeal::getStatus, STATUS_PENDING));
        return pending == null || pending == 0;
    }

    private String operatorRole(Long operatorId, HelpOrder order) {
        if (operatorId == null || operatorId == 0L) {
            return "SYSTEM";
        }
        if (operatorId.equals(order.getRequesterId())) {
            return "REQUESTER";
        }
        if (operatorId.equals(order.getHelperId())) {
            return "HELPER";
        }
        return "ADMIN";
    }

    private boolean isParticipant(HelpOrder order, Long uid) {
        return uid.equals(order.getRequesterId())
                || (order.getHelperId() != null && uid.equals(order.getHelperId()));
    }

    private void pushStatus(Long requesterId, Long helperId, Long orderId, String status, String title) {
        Object envelope = WsEnvelope.orderStatus(orderId, status, title);
        if (requesterId != null) {
            wsSessionRegistry.push(requesterId, envelope);
        }
        if (helperId != null) {
            wsSessionRegistry.push(helperId, envelope);
        }
    }

    private AppealVO toVO(Appeal appeal) {
        AppealVO vo = new AppealVO();
        vo.setId(appeal.getId());
        vo.setType(appeal.getType());
        vo.setOrderId(appeal.getOrderId());
        vo.setReason(appeal.getReason());
        vo.setEvidenceUrl(appeal.getEvidenceUrl());
        vo.setStatus(appeal.getStatus());
        vo.setConclusion(appeal.getConclusion());
        vo.setCreatedAt(appeal.getCreatedAt());
        vo.setResolvedAt(appeal.getResolvedAt());
        return vo;
    }

    private Map<Long, UserProfile> loadProfiles(List<Long> userIds) {
        List<Long> ids = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                        .in(UserProfile::getUserId, ids)).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
    }
}