package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.admin.dto.AdminOrderDetailVO;
import com.weiguang.modules.admin.dto.AdminOrderVO;
import com.weiguang.modules.admin.dto.AdminUserBriefVO;
import com.weiguang.modules.admin.dto.CancelArbitrateRequest;
import com.weiguang.modules.admin.dto.RiskReviewRequest;
import com.weiguang.modules.appeal.entity.Appeal;
import com.weiguang.modules.appeal.mapper.AppealMapper;
import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.TimelineVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.entity.OrderTimeline;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.mapper.OrderTimelineMapper;
import com.weiguang.modules.order.service.OrderTimelineRecorder;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端订单接口（详设 §3.4 / §6.3）：列表与详情（含时间线、双方、聊天记录门控）+ 协商取消裁决。
 */
@Slf4j
@Service
public class AdminOrderService {

    /** 管理端时间线操作人（恒定派生 ADMIN，见《实现偏差清单》D19） */
    private static final long ADMIN_TIMELINE_OPERATOR = Long.MAX_VALUE;
    /** 协商取消裁决可作用的未完成态（终态与申诉中不在内） */
    private static final Set<String> UNFINISHED_STATES = Set.of("PENDING", "ACCEPTED", "IN_SERVICE", "PENDING_CONFIRM");
    private static final int CONCLUSION_MAX = 200;
    private static final int CHAT_LIMIT = 200;

    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final OrderTimelineMapper orderTimelineMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final AppealMapper appealMapper;
    private final ReportMapper reportMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final NotifyService notifyService;
    private final WsSessionRegistry wsSessionRegistry;
    private final ConfigService configService;
    private final OrderRiskService orderRiskService;

    public AdminOrderService(HelpOrderMapper helpOrderMapper,
                             UserMapper userMapper,
                             UserProfileMapper userProfileMapper,
                             OrderTimelineMapper orderTimelineMapper,
                             ChatSessionMapper chatSessionMapper,
                             ChatMessageMapper chatMessageMapper,
                             AppealMapper appealMapper,
                             ReportMapper reportMapper,
                             OrderTimelineRecorder timelineRecorder,
                             NotifyService notifyService,
                             WsSessionRegistry wsSessionRegistry,
                             ConfigService configService,
                             OrderRiskService orderRiskService) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.orderTimelineMapper = orderTimelineMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.appealMapper = appealMapper;
        this.reportMapper = reportMapper;
        this.timelineRecorder = timelineRecorder;
        this.notifyService = notifyService;
        this.wsSessionRegistry = wsSessionRegistry;
        this.configService = configService;
        this.orderRiskService = orderRiskService;
    }

    /** GET /admin/orders 列表（状态/类型/关键词/风险筛选，分页） */
    public PageVO<AdminOrderVO> list(String status, String orderType, String keyword, String riskStatus,
                                     Long page, Long pageSize) {
        var wrapper = Wrappers.<HelpOrder>lambdaQuery()
                .eq(status != null && !status.isBlank(), HelpOrder::getStatus, status)
                .eq(orderType != null && !orderType.isBlank(), HelpOrder::getOrderType, orderType)
                .like(keyword != null && !keyword.isBlank(), HelpOrder::getTitle, keyword)
                .orderByDesc(HelpOrder::getId);
        boolean riskFilter = "SUSPICIOUS".equals(riskStatus) || "REVIEWED".equals(riskStatus);
        if (!riskFilter) {
            Page<HelpOrder> result = helpOrderMapper.selectPage(
                    new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);
            Map<Long, String> nicknames = nicknames(collectUserIds(result.getRecords()));
            return PageVO.of(result.convert(order -> toVO(order, nicknames)));
        }

        // 风险判定依赖实时配置，SQL 无法直接过滤：先取候选集，再在内存中筛选与分页
        List<HelpOrder> candidates = helpOrderMapper.selectList(wrapper);
        List<HelpOrder> filtered = candidates.stream()
                .filter(order -> {
                    boolean reviewed = order.getRiskReviewedAt() != null;
                    if ("REVIEWED".equals(riskStatus)) {
                        return reviewed;
                    }
                    return !reviewed && orderRiskService.evaluate(order).suspicious();
                })
                .toList();
        long current = PageUtils.normalizePage(page);
        long size = PageUtils.normalizeSize(pageSize);
        int from = (int) Math.min(filtered.size(), (current - 1) * size);
        int to = (int) Math.min(filtered.size(), from + size);
        List<HelpOrder> pageOrders = filtered.subList(from, to);
        Map<Long, String> nicknames = nicknames(collectUserIds(pageOrders));
        Page<AdminOrderVO> voPage = new Page<>(current, size);
        voPage.setRecords(pageOrders.stream().map(order -> toVO(order, nicknames)).toList());
        voPage.setTotal(filtered.size());
        return PageVO.of(voPage);
    }

    /** GET /admin/orders/{id} 详情（时间线 + 双方 + 聊天记录门控 + 裁决可用标记） */
    public AdminOrderDetailVO detail(Long id) {
        HelpOrder order = helpOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        AdminOrderDetailVO vo = new AdminOrderDetailVO();
        vo.setId(order.getId());
        vo.setTitle(order.getTitle());
        vo.setDescription(order.getDescription());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setUrgentFlag(order.getUrgentFlag());
        vo.setUrgencyScore(order.getUrgencyScore());
        vo.setTags(JsonUtils.toStringList(order.getTags()));
        vo.setTimeExpect(order.getTimeExpect());
        vo.setAddressArea(order.getAddressArea());
        vo.setAddressDetail(order.getAddressDetail());
        vo.setViewCount(order.getViewCount());
        vo.setCurrentRound(order.getCurrentRound());
        vo.setReleaseCount(order.getReleaseCount());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setPublishDeadline(order.getPublishDeadline());
        vo.setAcceptedAt(order.getAcceptedAt());
        vo.setStartedAt(order.getStartedAt());
        vo.setConfirmInitiator(order.getConfirmInitiator());
        vo.setConfirmDeadline(order.getConfirmDeadline());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCancelledAt(order.getCancelledAt());
        vo.setCancelBy(order.getCancelBy());
        vo.setCancelReason(order.getCancelReason());
        vo.setCancelPendingBy(order.getCancelPendingBy());
        vo.setCancelPendingDeadline(order.getCancelPendingDeadline());
        vo.setCancelArbitrateAt(order.getCancelArbitrateAt());
        vo.setAppealDeadline(order.getAppealDeadline());
        vo.setCompleteRequestedAt(order.getCompleteRequestedAt());
        OrderRiskService.RiskInfo risk = orderRiskService.evaluate(order);
        vo.setDurationMinutes(risk.evaluated() ? risk.durationMinutes() : null);
        vo.setMinMinutes(risk.evaluated() ? risk.minMinutes() : null);
        vo.setRiskCategories(risk.categories());
        vo.setSuspicious(risk.evaluated() ? risk.suspicious() : null);
        vo.setRiskReviewAvailable(risk.evaluated() && order.getRiskReviewedAt() == null);
        vo.setRiskReviewedAt(order.getRiskReviewedAt());
        vo.setRiskReviewResult(order.getRiskReviewResult());
        vo.setRiskReviewNote(order.getRiskReviewNote());
        vo.setRequester(brief(order.getRequesterId()));
        vo.setHelper(order.getHelperId() == null ? null : brief(order.getHelperId()));

        vo.setTimeline(orderTimelineMapper.selectList(Wrappers.<OrderTimeline>lambdaQuery()
                        .eq(OrderTimeline::getOrderId, id)
                        .orderByAsc(OrderTimeline::getId)).stream()
                .map(record -> {
                    TimelineVO t = new TimelineVO();
                    t.setEventType(record.getEventType());
                    t.setOperatorRole(operatorRole(record.getOperatorId(), order));
                    t.setRemark(record.getRemark());
                    t.setCreatedAt(record.getCreatedAt());
                    return t;
                }).toList());

        // 聊天记录仅当该单存在申诉或高危上报关联时返回（详设 §3.4）
        boolean chatAvailable = appealMapper.selectCount(Wrappers.<Appeal>lambdaQuery()
                .eq(Appeal::getOrderId, id)) > 0
                || reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getOrderId, id)) > 0;
        vo.setChatAvailable(chatAvailable);
        if (chatAvailable) {
            vo.setChatMessages(chatMessages(order));
        }

        vo.setCancelArbitrateAvailable(order.getCancelPendingBy() != null
                && UNFINISHED_STATES.contains(order.getStatus()));
        return vo;
    }

    /**
     * POST /admin/orders/{id}/cancel-arbitrate 协商取消裁决（详设 §3.4 判定顺序）：
     * ① 已裁决（cancel_arbitrate_at 非空）→ 40003；② 非挂起单或非未完成态 → 40002；
     * ③ 条件更新（防并发重复裁决，影响 0 行 → 40003）；CANCEL/KEEP 均写裁决时间，
     * 通知双方 ADMIN_ACTION·订单裁决（jump=ORDER、content 含 conclusion）。
     */
    @Transactional
    public void cancelArbitrate(Long adminId, Long orderId, CancelArbitrateRequest request) {
        String decision = request == null ? null : request.getDecision();
        String conclusion = request == null ? null : request.getConclusion();
        if (!"CANCEL".equals(decision) && !"KEEP".equals(decision)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (conclusion == null || conclusion.isBlank() || conclusion.length() > CONCLUSION_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (order.getCancelArbitrateAt() != null) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        if (order.getCancelPendingBy() == null || !UNFINISHED_STATES.contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        LocalDateTime now = LocalDateTime.now();
        var update = Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, order.getStatus())
                .isNotNull(HelpOrder::getCancelPendingBy)
                .isNull(HelpOrder::getCancelArbitrateAt)
                .set(HelpOrder::getCancelArbitrateAt, now)
                .set(HelpOrder::getCancelPendingBy, null)
                .set(HelpOrder::getCancelPendingDeadline, null);
        boolean toCancel = "CANCEL".equals(decision);
        if (toCancel) {
            update.set(HelpOrder::getStatus, "CANCELLED")
                    .set(HelpOrder::getCancelBy, "ADMIN")
                    .set(HelpOrder::getCancelReason, conclusion)
                    .set(HelpOrder::getCancelledAt, now)
                    .set(HelpOrder::getAppealDeadline,
                            now.plusDays(configService.getInt("order.appeal_window_days", 7)));
        }
        int rows = helpOrderMapper.update(null, update);
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }

        if (toCancel) {
            closeSession(order, now);
        }
        timelineRecorder.record(orderId, "ARBITRATE", ADMIN_TIMELINE_OPERATOR, conclusion);
        notifyService.adminActionOrderArbitrate(order.getRequesterId(), order.getTitle(), conclusion, orderId);
        if (order.getHelperId() != null) {
            notifyService.adminActionOrderArbitrate(order.getHelperId(), order.getTitle(), conclusion, orderId);
        }
        if (toCancel) {
            push(order, "CANCELLED");
        }
        log.info("协商取消裁决：order={} decision={} admin={}", orderId, decision, adminId);
    }

    /** POST /admin/orders/{id}/risk-review 风险核实（NORMAL/DEDUCT；扣分走既有用户微光值调整） */
    @Transactional
    public void riskReview(Long adminId, Long orderId, RiskReviewRequest request) {
        String result = request == null ? null : request.getResult();
        if (!"NORMAL".equals(result) && !"DEDUCT".equals(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String note = request.getNote() == null ? null : request.getNote().trim();
        if (note != null && note.length() > CONCLUSION_MAX) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        LocalDateTime now = LocalDateTime.now();
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, "COMPLETED")
                .set(HelpOrder::getRiskReviewedAt, now)
                .set(HelpOrder::getRiskReviewResult, result)
                .set(HelpOrder::getRiskReviewNote, note));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String remark = "风险核实：" + ("NORMAL".equals(result) ? "正常" : "已扣分")
                + (note == null || note.isBlank() ? "" : "（" + note + "）");
        timelineRecorder.record(orderId, "ADMIN_ACTION", ADMIN_TIMELINE_OPERATOR, remark);
        log.info("风险核实：order={} result={} admin={}", orderId, result, adminId);
    }

    private AdminOrderVO toVO(HelpOrder order, Map<Long, String> nicknames) {
        AdminOrderVO vo = new AdminOrderVO();
        vo.setId(order.getId());
        vo.setTitle(order.getTitle());
        vo.setOrderType(order.getOrderType());
        vo.setStatus(order.getStatus());
        vo.setUrgentFlag(order.getUrgentFlag());
        vo.setRequesterId(order.getRequesterId());
        vo.setRequesterNickname(nicknames.get(order.getRequesterId()));
        vo.setHelperId(order.getHelperId());
        vo.setHelperNickname(order.getHelperId() == null ? null : nicknames.get(order.getHelperId()));
        vo.setCancelPendingBy(order.getCancelPendingBy());
        vo.setCreatedAt(order.getCreatedAt());
        OrderRiskService.RiskInfo risk = orderRiskService.evaluate(order);
        vo.setDurationMinutes(risk.evaluated() ? risk.durationMinutes() : null);
        vo.setMinMinutes(risk.evaluated() ? risk.minMinutes() : null);
        vo.setRiskCategories(risk.categories());
        vo.setSuspicious(risk.evaluated() ? risk.suspicious() : null);
        vo.setRiskReviewedAt(order.getRiskReviewedAt());
        vo.setRiskReviewResult(order.getRiskReviewResult());
        return vo;
    }

    private Set<Long> collectUserIds(List<HelpOrder> orders) {
        Set<Long> ids = new HashSet<>();
        for (HelpOrder order : orders) {
            if (order.getRequesterId() != null) {
                ids.add(order.getRequesterId());
            }
            if (order.getHelperId() != null) {
                ids.add(order.getHelperId());
            }
        }
        return ids;
    }

    private Map<Long, String> nicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                        .in(UserProfile::getUserId, userIds)).stream()
                .collect(Collectors.toMap(UserProfile::getUserId,
                        profile -> profile.getNickname() == null ? "" : profile.getNickname(), (a, b) -> a));
    }

    private AdminUserBriefVO brief(Long uid) {
        User user = userMapper.selectById(uid);
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, uid));
        AdminUserBriefVO vo = new AdminUserBriefVO();
        vo.setId(uid);
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        vo.setStatus(user == null ? null : user.getStatus());
        return vo;
    }

    private List<MessageVO> chatMessages(HelpOrder order) {
        ChatSession session = chatSessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound())
                .orderByDesc(ChatSession::getId)
                .last("LIMIT 1"));
        if (session == null) {
            return List.of();
        }
        return chatMessageMapper.selectList(Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, session.getId())
                        .orderByAsc(ChatMessage::getId)
                        .last("LIMIT " + CHAT_LIMIT)).stream()
                .map(message -> {
                    MessageVO m = new MessageVO();
                    m.setMsgId(message.getId());
                    m.setSenderId(message.getSenderId());
                    m.setMessageType(message.getMessageType());
                    m.setContent(message.getContent());
                    m.setRiskFlag(message.getRiskFlag());
                    m.setCreatedAt(message.getCreatedAt());
                    return m;
                }).toList();
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

    private void closeSession(HelpOrder order, LocalDateTime now) {
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound())
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
}