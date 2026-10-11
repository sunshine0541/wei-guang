package com.weiguang.modules.chat.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.constants.QuickPhraseConstants;
import com.weiguang.common.enums.OrderType;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.chat.dto.ReadVO;
import com.weiguang.modules.chat.dto.SendMessageRequest;
import com.weiguang.modules.chat.dto.SessionVO;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.security.service.FraudPipeline;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 聊天（详设 §3.3 C1–C5、§3.5）：
 * 会话获取、历史消息、REST 发送（client_msg_id 幂等）、已读清零、快捷短语；
 * 未读数维护、WS 在线推送、离线落 NEW_MESSAGE 通知。
 */
@Service
public class ChatService {

    private static final String BANNED_MESSAGE = "您的账号暂时被限制了，如有疑问可在'反馈与申诉'中留言";
    private static final Set<String> VALID_MESSAGE_TYPES = Set.of("TEXT", "IMAGE");
    /** 门牌解锁：上门类"确认开始后"至服务结束 */
    private static final Set<String> DETAIL_VISIBLE_STATES =
            Set.of("IN_SERVICE", "PENDING_CONFIRM", "APPEALING");
    /** 紧急联系人可见：被接单后至服务结束（详设 §2.7 / P08） */
    private static final Set<String> CONTACT_VISIBLE_STATES =
            Set.of("ACCEPTED", "IN_SERVICE", "PENDING_CONFIRM", "APPEALING");
    private static final int CLIENT_MSG_ID_MAX_LENGTH = 64;
    private static final int CONTENT_COLUMN_MAX_LENGTH = 500;

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ConfigService configService;
    private final FraudPipeline fraudPipeline;
    private final NotifyService notifyService;
    private final WsSessionRegistry wsSessionRegistry;
    private final TransactionTemplate transactionTemplate;

    public ChatService(ChatSessionMapper chatSessionMapper,
                       ChatMessageMapper chatMessageMapper,
                       HelpOrderMapper helpOrderMapper,
                       UserMapper userMapper,
                       UserProfileMapper userProfileMapper,
                       ConfigService configService,
                       FraudPipeline fraudPipeline,
                       NotifyService notifyService,
                       WsSessionRegistry wsSessionRegistry,
                       TransactionTemplate transactionTemplate) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.configService = configService;
        this.fraudPipeline = fraudPipeline;
        this.notifyService = notifyService;
        this.wsSessionRegistry = wsSessionRegistry;
        this.transactionTemplate = transactionTemplate;
    }

    /** C1 当前轮会话（不存在 → 40002；含接单者专属地址与紧急联系人，见 D10） */
    public SessionVO getSession(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean isHelper = uid.equals(order.getHelperId());
        boolean isRequester = uid.equals(order.getRequesterId());
        if (!isHelper && !isRequester) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        ChatSession session = chatSessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getOrderId, orderId)
                .eq(ChatSession::getRound, order.getCurrentRound() == null ? 1 : order.getCurrentRound()));
        if (session == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        SessionVO vo = new SessionVO();
        vo.setSessionId(session.getId());
        vo.setRound(session.getRound());
        vo.setStatus(session.getStatus());
        UserProfile peerProfile = findProfile(isHelper ? order.getRequesterId() : order.getHelperId());
        vo.setPeerNickname(peerProfile == null ? null : peerProfile.getNickname());
        vo.setPeerAvatarUrl(peerProfile == null ? null : peerProfile.getAvatarUrl());
        vo.setMyUnread(isHelper
                ? (session.getHelperUnread() == null ? 0 : session.getHelperUnread())
                : (session.getRequesterUnread() == null ? 0 : session.getRequesterUnread()));
        vo.setMyUserId(uid);

        boolean remote = OrderType.REMOTE.name().equals(order.getOrderType());
        if (isHelper && !remote) {
            // 陪同类=集合点；上门类=小区/区域（接单后可见）
            vo.setAddressArea(order.getAddressArea());
            if (OrderType.HOME_VISIT.name().equals(order.getOrderType())
                    && DETAIL_VISIBLE_STATES.contains(order.getStatus())) {
                vo.setAddressDetail(order.getAddressDetail());
            }
            if (CONTACT_VISIBLE_STATES.contains(order.getStatus())) {
                UserProfile requesterProfile = findProfile(order.getRequesterId());
                if (requesterProfile != null) {
                    vo.setEmergencyContactName(requesterProfile.getEmergencyContactName());
                    vo.setEmergencyContactPhone(requesterProfile.getEmergencyContactPhone());
                }
            }
        }
        return vo;
    }

    /** C2 消息历史（分页，倒序） */
    public PageVO<MessageVO> messages(Long uid, Long sessionId, Long page, Long pageSize) {
        requireParticipant(uid, sessionId);
        Page<ChatMessage> result = chatMessageMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getSessionId, sessionId)
                        .orderByDesc(ChatMessage::getId));
        return PageVO.of(result.convert(this::toVO));
    }

    /** C3 发送（REST 兜底通道；client_msg_id 幂等，冲突返回原消息体） */
    public MessageVO send(Long uid, Long sessionId, SendMessageRequest request) {
        ChatSession session = requireParticipant(uid, sessionId);
        if (!"ACTIVE".equals(session.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        User sender = userMapper.selectById(uid);
        if (sender == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        if ("BANNED".equals(sender.getStatus())) {
            throw new BizException(ErrorCode.ACCOUNT_RESTRICTED, BANNED_MESSAGE);
        }
        validate(request);

        // 详设 §5.3 防诈管线第 1、2 层：本地分级（HIGHLIGHT 高亮；防诈要素+助人者→求助者 → 交能力二异步复核）
        FraudPipeline.LocalDecision decision = "TEXT".equals(request.getMessageType())
                ? fraudPipeline.evaluateLocal(request.getContent(), uid.equals(session.getHelperId()))
                : new FraudPipeline.LocalDecision(FraudPipeline.FLAG_NONE, false, List.of());

        MessageVO message;
        boolean fresh = true;
        try {
            message = transactionTemplate.execute(status -> persist(session, uid, request, decision.riskFlag()));
        } catch (DuplicateKeyException e) {
            fresh = false;
            message = findByClientMsgId(request.getClientMsgId());
            if (message == null) {
                throw e;
            }
        }
        pushAfterSend(session, uid, message, request.getClientMsgId());
        if (fresh && decision.reviewRequired()) {
            // 第 3 层：异步复核（不阻塞发送、不在事务内）；HIGH → STRONG+上报+强提示，否则维持 HIGHLIGHT
            fraudPipeline.reviewAsync(session.getOrderId(), message.getMsgId(), uid,
                    session.getRequesterId(), request.getContent(), decision.hitWords());
        }
        return message;
    }

    /** C4 已读（未读数清零，幂等） */
    public ReadVO read(Long uid, Long sessionId) {
        ChatSession session = requireParticipant(uid, sessionId);
        boolean isHelper = uid.equals(session.getHelperId());
        Integer current = isHelper ? session.getHelperUnread() : session.getRequesterUnread();
        int cleared = current == null ? 0 : current;
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, sessionId)
                .set(isHelper ? ChatSession::getHelperUnread : ChatSession::getRequesterUnread, 0));
        return new ReadVO(cleared);
    }

    /** C5 快捷短语（固定 5 条） */
    public List<String> quickPhrases() {
        return QuickPhraseConstants.QUICK_PHRASES;
    }

    private MessageVO persist(ChatSession session, Long uid, SendMessageRequest request, String riskFlag) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setSenderId(uid);
        message.setMessageType(request.getMessageType());
        message.setContent(request.getContent());
        message.setClientMsgId(request.getClientMsgId());
        message.setRiskFlag(riskFlag);
        message.setCreatedAt(LocalDateTime.now());
        chatMessageMapper.insert(message);

        boolean senderIsRequester = uid.equals(session.getRequesterId());
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getId, session.getId())
                .setSql(senderIsRequester ? "helper_unread = helper_unread + 1" : "requester_unread = requester_unread + 1"));
        return toVO(message);
    }

    /** 事务外：WS 推送（在线）；对方离线则落 NEW_MESSAGE 通知；发送方同路径回执 */
    private void pushAfterSend(ChatSession session, Long uid, MessageVO message, String clientMsgId) {
        Long peerId = uid.equals(session.getRequesterId()) ? session.getHelperId() : session.getRequesterId();
        Object envelope = WsEnvelope.chatMessage(session.getId(), message, null);
        if (!wsSessionRegistry.push(peerId, envelope)) {
            notifyService.newMessage(peerId, nicknameOf(uid), session.getOrderId());
        }
        wsSessionRegistry.push(uid, WsEnvelope.chatMessage(session.getId(), message, clientMsgId));
    }

    private void validate(SendMessageRequest request) {
        if (request == null
                || request.getMessageType() == null
                || !VALID_MESSAGE_TYPES.contains(request.getMessageType())
                || request.getClientMsgId() == null
                || request.getClientMsgId().isBlank()
                || request.getClientMsgId().length() > CLIENT_MSG_ID_MAX_LENGTH
                || request.getContent() == null
                || request.getContent().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        int maxTextLength = configService.getInt("content.chat_text_max_length", 500);
        if ("TEXT".equals(request.getMessageType()) && request.getContent().length() > maxTextLength) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        if (request.getContent().length() > CONTENT_COLUMN_MAX_LENGTH) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
    }

    private ChatSession requireParticipant(Long uid, Long sessionId) {
        ChatSession session = chatSessionMapper.selectById(sessionId);
        if (session == null
                || (!uid.equals(session.getRequesterId()) && !uid.equals(session.getHelperId()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private MessageVO findByClientMsgId(String clientMsgId) {
        ChatMessage existing = chatMessageMapper.selectOne(Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getClientMsgId, clientMsgId));
        return existing == null ? null : toVO(existing);
    }

    private MessageVO toVO(ChatMessage message) {
        MessageVO vo = new MessageVO();
        vo.setMsgId(message.getId());
        vo.setSenderId(message.getSenderId());
        vo.setMessageType(message.getMessageType());
        vo.setContent(message.getContent());
        vo.setRiskFlag(message.getRiskFlag());
        vo.setCreatedAt(message.getCreatedAt());
        return vo;
    }

    private String nicknameOf(Long uid) {
        UserProfile profile = findProfile(uid);
        return profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname();
    }

    private UserProfile findProfile(Long uid) {
        if (uid == null) {
            return null;
        }
        return userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, uid));
    }
}