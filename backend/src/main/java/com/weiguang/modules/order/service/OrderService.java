package com.weiguang.modules.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.common.constants.HelpTagConstants;
import com.weiguang.common.enums.OrderSource;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.enums.OrderType;
import com.weiguang.common.enums.TimeExpect;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.config.BizMetrics;
import com.weiguang.infra.ratelimit.RedisHealthProbe;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.incentive.entity.Evaluation;
import com.weiguang.modules.incentive.mapper.EvaluationMapper;
import com.weiguang.modules.light.service.LightService;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.dto.AcceptVO;
import com.weiguang.modules.order.dto.CareTipVO;
import com.weiguang.modules.order.dto.MyOrderVO;
import com.weiguang.modules.order.dto.ModifyOrderRequest;
import com.weiguang.modules.order.dto.OrderCardVO;
import com.weiguang.modules.order.dto.OrderDetailVO;
import com.weiguang.modules.order.dto.PublishRequest;
import com.weiguang.modules.order.dto.PublishVO;
import com.weiguang.modules.order.dto.RequesterVO;
import com.weiguang.modules.order.dto.TimelineVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.entity.OrderTimeline;
import com.weiguang.modules.order.entity.OrderView;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.mapper.OrderTimelineMapper;
import com.weiguang.modules.order.mapper.OrderViewMapper;
import com.weiguang.modules.security.service.SensitiveWordService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.AddressBook;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.AddressBookMapper;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 求助单：发布（O2）、再来一次/一键重发（O13）、列表（O3）、详情与浏览去重（O4）、我的求助（O5）。
 * 能力一入口（O1）见 {@link #aiSuggest}。
 */
@Slf4j
@Service
public class OrderService {

    private static final String BLOCK_MESSAGE = "这段话里有些内容我们可能帮不上，换一种说法试试？";
    private static final String PENDING_LIMIT_MESSAGE = "您还有求助在等待邻居响应，先等它们被看到，再发新的吧";
    private static final String EMERGENCY_CONTACT_MESSAGE = "上门/外出类求助需要先填写紧急联系人的姓名和电话，方便服务过程中有突发情况时联系";
    private static final String BROWSE_DEDUP_KEY_PREFIX = "browse:dedup:";
    private static final int AI_SUGGEST_TEXT_MAX_LENGTH = 500;
    /** O15 照护提示卡可见状态：接单后至服务结束，结束即收回 */
    private static final java.util.Set<String> CARE_TIP_VISIBLE_STATES = java.util.Set.of(
            OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name(),
            OrderStatus.PENDING_CONFIRM.name(), OrderStatus.APPEALING.name());

    private final HelpOrderMapper helpOrderMapper;
    private final OrderViewMapper orderViewMapper;
    private final OrderTimelineMapper orderTimelineMapper;
    private final AddressBookMapper addressBookMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final ConfigService configService;
    private final SensitiveWordService sensitiveWordService;
    private final AiService aiService;
    private final OrderTimelineRecorder timelineRecorder;
    private final StringRedisTemplate redisTemplate;
    private final RedisHealthProbe redisHealthProbe;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final NotifyService notifyService;
    private final LightService lightService;
    private final BizMetrics bizMetrics;
    private final WsSessionRegistry wsSessionRegistry;
    private final com.weiguang.modules.appeal.service.AppealService appealService;
    private final EvaluationMapper evaluationMapper;

    public OrderService(HelpOrderMapper helpOrderMapper,
                        OrderViewMapper orderViewMapper,
                        OrderTimelineMapper orderTimelineMapper,
                        AddressBookMapper addressBookMapper,
                        UserMapper userMapper,
                        UserProfileMapper userProfileMapper,
                        ConfigService configService,
                        SensitiveWordService sensitiveWordService,
                        AiService aiService,
                        OrderTimelineRecorder timelineRecorder,
                        StringRedisTemplate redisTemplate,
                        RedisHealthProbe redisHealthProbe,
                        ChatSessionMapper chatSessionMapper,
                        ChatMessageMapper chatMessageMapper,
                        NotifyService notifyService,
                        LightService lightService,
                        BizMetrics bizMetrics,
                        WsSessionRegistry wsSessionRegistry,
                        com.weiguang.modules.appeal.service.AppealService appealService,
                        EvaluationMapper evaluationMapper) {
        this.helpOrderMapper = helpOrderMapper;
        this.orderViewMapper = orderViewMapper;
        this.orderTimelineMapper = orderTimelineMapper;
        this.addressBookMapper = addressBookMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.configService = configService;
        this.sensitiveWordService = sensitiveWordService;
        this.aiService = aiService;
        this.timelineRecorder = timelineRecorder;
        this.redisTemplate = redisTemplate;
        this.redisHealthProbe = redisHealthProbe;
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.notifyService = notifyService;
        this.lightService = lightService;
        this.bizMetrics = bizMetrics;
        this.wsSessionRegistry = wsSessionRegistry;
        this.appealService = appealService;
        this.evaluationMapper = evaluationMapper;
    }

    /** O1 能力一 · 求助理解：先本地敏感词（仅 BLOCK 拦截），否则交 AI（Mock 模式为本地规则） */
    public UnderstandHelpResult aiSuggest(String text) {
        if (text == null || text.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (text.length() > AI_SUGGEST_TEXT_MAX_LENGTH) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        if (sensitiveWordService.hit(text, SensitiveWordService.ACTION_BLOCK)) {
            throw new BizException(ErrorCode.CONTENT_BLOCKED, BLOCK_MESSAGE);
        }
        return aiService.understandHelp(text);
    }

    /** O2 发布求助（限流 3 次/分由 @RateLimit 声明） */
    @Transactional
    public PublishVO publish(Long uid, PublishRequest request) {
        User user = requireUser(uid);
        UserProfile profile = findProfile(uid);

        String source = request.getSource() == null || request.getSource().isBlank()
                ? OrderSource.FREE.name() : request.getSource();
        if (!OrderSource.isValid(source)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (!OrderType.isValid(request.getOrderType())) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        OrderType orderType = OrderType.valueOf(request.getOrderType());

        validateTitleAndDescription(request.getTitle(), request.getDescription());
        List<String> tags = validateTags(request.getTags());
        if (request.getTimeExpect() != null && !TimeExpect.isValid(request.getTimeExpect())) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }

        AddressSnapshot address = resolveAddress(uid, orderType, request.getAddressId());
        requireEmergencyContact(profile, orderType);

        if (OrderSource.REPEAT.name().equals(source)) {
            // 「再来一次」允许选本人已了结的求助（完成/取消/超时）；一键重发 O13 仍仅限 EXPIRED
            requireOwnRepeatableOrder(uid, request.getRepeatFromOrderId());
        }

        String content = (request.getTitle() == null ? "" : request.getTitle())
                + (request.getDescription() == null ? "" : request.getDescription());
        if (sensitiveWordService.hit(content, SensitiveWordService.ACTION_BLOCK)) {
            throw new BizException(ErrorCode.CONTENT_BLOCKED, BLOCK_MESSAGE);
        }

        requirePendingLimit(uid);

        int urgentFlag = request.getUrgentFlag() != null && request.getUrgentFlag() == 1 ? 1 : 0;
        HelpOrder order = buildOrder(uid, user, profile, source, orderType, urgentFlag,
                request.getTitle(), request.getDescription(), tags, request.getTimeExpect(),
                address.area(), address.detail(), address.lat(), address.lng(),
                normalizeExtraTag(request.getExtraTag()));
        helpOrderMapper.insert(order);
        timelineRecorder.record(order.getId(), "PUBLISH", uid, null);
        touchInteraction(uid);
        // 光语映射（详设 §4.2）：常亮者发布求助 → 小憩
        lightService.onPublish(uid);

        boolean careNotice = isNightTime() && urgentFlag == 0;
        return new PublishVO(order.getId(), careNotice);
    }

    /** O13 一键重发：仅发布者、仅 EXPIRED 态，复制为新单（待响应上限与 O2 同口径，见 D04） */
    @Transactional
    public PublishVO republish(Long uid, Long orderId) {
        User user = requireUser(uid);
        UserProfile profile = findProfile(uid);
        HelpOrder origin = requireOwnExpiredOrder(uid, orderId);
        requirePendingLimit(uid);

        OrderType orderType = OrderType.valueOf(origin.getOrderType());
        requireEmergencyContact(profile, orderType);
        HelpOrder order = buildOrder(uid, user, profile, OrderSource.REPEAT.name(), orderType,
                origin.getUrgentFlag() == null ? 0 : origin.getUrgentFlag(),
                origin.getTitle(), origin.getDescription(), JsonUtils.toStringList(origin.getTags()),
                origin.getTimeExpect(), origin.getAddressArea(), origin.getAddressDetail(),
                origin.getAddressLat(), origin.getAddressLng(), origin.getExtraTag());
        helpOrderMapper.insert(order);
        timelineRecorder.record(order.getId(), "PUBLISH", uid, "一键重发");
        timelineRecorder.record(origin.getId(), "REPUBLISH", uid, "已重发为新单 " + order.getId());
        touchInteraction(uid);
        // 一键重发同属发布事件（详设 §4.2）
        lightService.onPublish(uid);
        return new PublishVO(order.getId(), false);
    }

    /** O16 修改待响应求助：仅发布者、仅 PENDING；类型与地址不可改（D53） */
    @Transactional
    public void modify(Long uid, Long orderId, ModifyOrderRequest request) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null || !uid.equals(order.getRequesterId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        validateTitleAndDescription(request.getTitle(), request.getDescription());
        List<String> tags = request.getTags() == null
                ? JsonUtils.toStringList(order.getTags()) : validateTags(request.getTags());
        String timeExpect = request.getTimeExpect() == null ? order.getTimeExpect() : request.getTimeExpect();
        if (timeExpect != null && !TimeExpect.isValid(timeExpect)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String content = (request.getTitle() == null ? "" : request.getTitle())
                + (request.getDescription() == null ? "" : request.getDescription());
        if (sensitiveWordService.hit(content, SensitiveWordService.ACTION_BLOCK)) {
            throw new BizException(ErrorCode.CONTENT_BLOCKED, BLOCK_MESSAGE);
        }

        int existingUrgent = order.getUrgentFlag() == null ? 0 : order.getUrgentFlag();
        int urgentFlag = request.getUrgentFlag() == null
                ? existingUrgent : (request.getUrgentFlag() == 1 ? 1 : 0);
        OrderType orderType = OrderType.valueOf(order.getOrderType());
        int urgencyBase = configService.getInt(orderType.urgencyBaseConfigKey(), 1);
        int urgencyScore = urgencyBase + urgentFlag * configService.getInt("urgency.urgent_weight", 3);
        // 截止时间：只有「希望什么时候」或急标变化时才重算，避免靠编辑续命
        boolean deadlineChanged = urgentFlag != existingUrgent
                || (timeExpect != null && !timeExpect.equals(order.getTimeExpect()));
        LocalDateTime publishDeadline = deadlineChanged
                ? PublishDeadlinePolicy.resolve(
                        timeExpect,
                        urgentFlag,
                        configService.getInt("order.pending_timeout_urgent_hours", 6),
                        configService.getInt("order.pending_timeout_hours", 24),
                        configService.getInt("order.zombie_clean_days", 7))
                : order.getPublishDeadline();
        String extraTag = request.getExtraTag() == null
                ? order.getExtraTag() : normalizeExtraTag(request.getExtraTag());
        UserProfile profile = findProfile(uid);
        Integer age = profile == null ? null : profile.getAge();
        int agePriority = age != null && age >= configService.getInt("elder.age_threshold", 60) ? 1 : 0;

        int version = order.getVersion() == null ? 0 : order.getVersion();
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .eq(HelpOrder::getVersion, version)
                .set(HelpOrder::getTitle, request.getTitle())
                .set(HelpOrder::getDescription, request.getDescription())
                .set(HelpOrder::getTags, JsonUtils.toJson(tags))
                .set(HelpOrder::getExtraTag, extraTag)
                .set(HelpOrder::getTimeExpect, timeExpect)
                .set(HelpOrder::getUrgentFlag, urgentFlag)
                .set(HelpOrder::getUrgencyBase, urgencyBase)
                .set(HelpOrder::getUrgencyScore, urgencyScore)
                .set(HelpOrder::getAgePriority, agePriority)
                .set(HelpOrder::getPublishDeadline, publishDeadline)
                .set(HelpOrder::getVersion, version + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        timelineRecorder.record(orderId, "MODIFY", uid, "修改了求助内容");
    }

    /**
     * O6 接单（"我愿意帮助"）：核心并发点。
     * 封禁用户由 UserAuthInterceptor 统一拦写（20004），此处不再重复校验。
     */
    @Transactional
    public AcceptVO accept(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (order.getRequesterId().equals(uid)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (!OrderStatus.PENDING.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.ACCEPT_CONFLICT);
        }

        User helper = requireUser(uid);
        UserProfile helperProfile = findProfile(uid);
        requireAcceptGates(helper, helperProfile, OrderType.valueOf(order.getOrderType()));

        LocalDateTime now = LocalDateTime.now();
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name())
                .eq(HelpOrder::getVersion, order.getVersion())
                .set(HelpOrder::getStatus, OrderStatus.ACCEPTED.name())
                .set(HelpOrder::getHelperId, uid)
                .set(HelpOrder::getAcceptedAt, now)
                .set(HelpOrder::getStartDeadline,
                        now.plusHours(configService.getInt("order.accept_timeout_hours", 2)))
                .set(HelpOrder::getVersion, order.getVersion() + 1));
        if (rows == 0) {
            throw new BizException(ErrorCode.ACCEPT_CONFLICT);
        }

        ChatSession session = new ChatSession();
        session.setOrderId(orderId);
        session.setRound(order.getCurrentRound() == null ? 1 : order.getCurrentRound());
        session.setRequesterId(order.getRequesterId());
        session.setHelperId(uid);
        session.setStatus("ACTIVE");
        session.setRequesterUnread(0);
        session.setHelperUnread(0);
        chatSessionMapper.insert(session);

        timelineRecorder.record(orderId, "ACCEPT", uid, null);
        touchInteraction(uid);
        notifyService.accepted(order.getRequesterId(),
                helperProfile == null || helperProfile.getNickname() == null
                        ? "邻居" : helperProfile.getNickname(),
                order.getTitle(), orderId);
        bizMetrics.orderAccepted();
        pushOrderStatus(orderId, OrderStatus.ACCEPTED.name(), order.getTitle(), order.getRequesterId(), uid);
        return new AcceptVO(session.getId());
    }

    /**
     * O7 确认开始：仅当前轮接单者；ACCEPTED → IN_SERVICE（详设 §3.3）。
     * 事务内：时间线(START) + 双方互动时间 + 会话系统消息"邻居已确认开始帮忙"；通知求助者 SERVICE_STARTED。
     */
    @Transactional
    public void start(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!uid.equals(order.getHelperId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                .eq(HelpOrder::getId, orderId)
                .eq(HelpOrder::getStatus, OrderStatus.ACCEPTED.name())
                .eq(HelpOrder::getHelperId, uid)
                .set(HelpOrder::getStatus, OrderStatus.IN_SERVICE.name())
                .set(HelpOrder::getStartedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }

        int round = order.getCurrentRound() == null ? 1 : order.getCurrentRound();
        ChatSession session = chatSessionMapper.selectOne(Wrappers.<ChatSession>lambdaQuery()
                .eq(ChatSession::getOrderId, orderId)
                .eq(ChatSession::getRound, round));
        if (session != null) {
            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setSessionId(session.getId());
            systemMessage.setSenderId(0L);
            systemMessage.setMessageType("SYSTEM");
            systemMessage.setContent("邻居已确认开始帮忙");
            systemMessage.setRiskFlag("NONE");
            systemMessage.setCreatedAt(LocalDateTime.now());
            chatMessageMapper.insert(systemMessage);
        }

        timelineRecorder.record(orderId, "START", uid, null);
        touchInteraction(uid);
        touchInteraction(order.getRequesterId());
        notifyService.serviceStarted(order.getRequesterId(), nicknameOf(uid), order.getTitle(), orderId);
        pushOrderStatus(orderId, OrderStatus.IN_SERVICE.name(), order.getTitle(), order.getRequesterId(), uid);
    }

    /** O14 订单时间线（归属=订单双方，按时间升序，operatorRole 派生） */
    public List<TimelineVO> timeline(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null
                || (!uid.equals(order.getRequesterId()) && !uid.equals(order.getHelperId()))) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        List<OrderTimeline> records = orderTimelineMapper.selectList(Wrappers.<OrderTimeline>lambdaQuery()
                .eq(OrderTimeline::getOrderId, orderId)
                .orderByAsc(OrderTimeline::getId));
        return records.stream().map(record -> {
            TimelineVO vo = new TimelineVO();
            vo.setEventType(record.getEventType());
            vo.setOperatorRole(operatorRole(record.getOperatorId(), order));
            vo.setRemark(record.getRemark());
            vo.setCreatedAt(record.getCreatedAt());
            return vo;
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

    private void pushOrderStatus(Long orderId, String status, String title, Long requesterId, Long helperId) {
        Object envelope = WsEnvelope.orderStatus(orderId, status, title);
        wsSessionRegistry.push(requesterId, envelope);
        if (helperId != null) {
            wsSessionRegistry.push(helperId, envelope);
        }
    }

    private String nicknameOf(Long uid) {
        UserProfile profile = findProfile(uid);
        return profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname();
    }

    /** O6 类型门槛：上门/陪同需年龄已填且 ≥18、微光值 ≥`credit.threshold`、不在冷却；远程无门槛 */
    private void requireAcceptGates(User helper, UserProfile profile, OrderType orderType) {
        if (orderType == OrderType.REMOTE) {
            return;
        }
        if (profile == null || profile.getAge() == null || profile.getAge() < 18) {
            throw new BizException(ErrorCode.AGE_REQUIRED);
        }
        if (helper.getCreditScore() == null
                || helper.getCreditScore() < configService.getInt("credit.threshold", 60)) {
            throw new BizException(ErrorCode.CREDIT_INSUFFICIENT);
        }
        LocalDateTime cooldownUntil = helper.getCooldownUntil();
        if (cooldownUntil == null) {
            return;
        }
        if (cooldownUntil.isAfter(LocalDateTime.now())) {
            throw new BizException(ErrorCode.ACCEPT_COOLDOWN);
        }
        // 冷却已到期：懒清零（详设 §4.1 违约冷却）
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, helper.getId())
                .set(User::getCooldownUntil, null)
                .set(User::getViolationCount, 0));
    }

    /** O15 照护提示卡：仅当前轮接单者且服务有效状态内；结束/非接单者 → 20003 收回 */
    public CareTipVO careTip(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null
                || !uid.equals(order.getHelperId())
                || !CARE_TIP_VISIBLE_STATES.contains(order.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        UserProfile requester = findProfile(order.getRequesterId());
        return new CareTipVO(
                requester == null ? null : requester.getNickname(),
                requester == null ? null : requester.getCareTip());
    }

    /** O3 求助列表（tab=all|recommend；sort=distance 时按直线距离升序，无坐标/本人单置后） */
    public PageVO<OrderCardVO> list(Long uid, String tab, String orderType, Boolean urgentOnly,
                                    String sort, Long page, Long pageSize) {
        if (orderType != null && !orderType.isBlank() && !OrderType.isValid(orderType)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        List<String> directions = List.of();
        if ("recommend".equals(tab)) {
            UserProfile profile = findProfile(uid);
            directions = profile == null ? List.of() : JsonUtils.toStringList(profile.getHelpDirections());
            if (directions.isEmpty()) {
                return PageVO.of(new Page<>(1, PageUtils.normalizeSize(pageSize)));
            }
        }

        LambdaQueryWrapper<HelpOrder> wrapper = Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name());
        if (orderType != null && !orderType.isBlank()) {
            wrapper.eq(HelpOrder::getOrderType, orderType);
        }
        if (Boolean.TRUE.equals(urgentOnly)) {
            wrapper.eq(HelpOrder::getUrgentFlag, 1);
        }
        if (!directions.isEmpty()) {
            List<String> matched = directions;
            wrapper.and(inner -> {
                for (String direction : matched) {
                    inner.or().like(HelpOrder::getTags, "\"" + direction + "\"");
                }
            });
        }

        AddressBook myDefault = defaultAddress(uid);
        boolean distanceSort = "distance".equals(sort) && hasGeo(myDefault);
        if (distanceSort) {
            // 距离排序在内存中完成：无坐标/本人发布的单排在后面，其余按直线距离升序
            List<HelpOrder> candidates = helpOrderMapper.selectList(wrapper);
            Map<Long, Double> distances = new HashMap<>();
            for (HelpOrder order : candidates) {
                distances.put(order.getId(), order.getRequesterId().equals(uid)
                        ? null : distanceKm(myDefault, order));
            }
            candidates.sort((a, b) -> {
                Double da = distances.get(a.getId());
                Double db = distances.get(b.getId());
                if (da == null && db == null) {
                    return compareUrgency(a, b);
                }
                if (da == null) {
                    return 1;
                }
                if (db == null) {
                    return -1;
                }
                int cmp = Double.compare(da, db);
                return cmp != 0 ? cmp : compareUrgency(a, b);
            });
            long current = PageUtils.normalizePage(page);
            long size = PageUtils.normalizeSize(pageSize);
            int from = (int) Math.min(candidates.size(), (current - 1) * size);
            int to = (int) Math.min(candidates.size(), from + size);
            List<HelpOrder> pageOrders = candidates.subList(from, to);
            Map<Long, UserProfile> profiles = loadProfiles(pageOrders.stream()
                    .map(HelpOrder::getRequesterId).toList());
            Page<OrderCardVO> voPage = new Page<>(current, size);
            voPage.setRecords(pageOrders.stream()
                    .map(order -> toCard(order, profiles.get(order.getRequesterId()), uid, myDefault))
                    .toList());
            voPage.setTotal(candidates.size());
            return PageVO.of(voPage);
        }

        wrapper.orderByDesc(HelpOrder::getUrgencyScore)
                .orderByDesc(HelpOrder::getAgePriority)
                .orderByDesc(HelpOrder::getCreatedAt);

        Page<HelpOrder> result = helpOrderMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);
        Map<Long, UserProfile> profiles = loadProfiles(result.getRecords().stream()
                .map(HelpOrder::getRequesterId).toList());
        return PageVO.of(result.convert(order ->
                toCard(order, profiles.get(order.getRequesterId()), uid, myDefault)));
    }

    /** O4 求助详情 + 浏览去重（Redis 预检 + order_view 唯一键；发布者本人不计数） */
    @Transactional
    public OrderDetailVO detail(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean mine = order.getRequesterId().equals(uid);
        if (!mine && markViewed(orderId, uid)) {
            Long count = orderViewMapper.selectCount(
                    Wrappers.<OrderView>lambdaQuery().eq(OrderView::getOrderId, orderId));
            long viewCount = count == null ? 0L : count;
            helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getId, orderId)
                    .set(HelpOrder::getViewCount, viewCount));
            order.setViewCount((int) viewCount);
        }

        UserProfile profile = findProfile(order.getRequesterId());
        OrderDetailVO vo = new OrderDetailVO();
        vo.setId(order.getId());
        vo.setTitle(order.getTitle());
        vo.setOrderType(order.getOrderType());
        vo.setTags(JsonUtils.toStringList(order.getTags()));
        vo.setExtraTag(order.getExtraTag());
        vo.setUrgentFlag(order.getUrgentFlag());
        vo.setDescription(order.getDescription());
        vo.setTimeExpect(order.getTimeExpect());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setStartedAt(order.getStartedAt());
        vo.setCompletedAt(order.getCompletedAt());
        vo.setCompleteRequestedAt(order.getCompleteRequestedAt());
        vo.setViewCount(order.getViewCount());
        vo.setStatus(order.getStatus());
        String myRole = mine ? "REQUESTER" : (uid.equals(order.getHelperId()) ? "HELPER" : null);
        vo.setMyRole(myRole);
        // 助人者看已完成的单：带出受助者给的星级（无评价则为 null，前端不渲染）
        if ("HELPER".equals(myRole) && OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            Evaluation evaluation = evaluationMapper.selectOne(Wrappers.<Evaluation>lambdaQuery()
                    .eq(Evaluation::getOrderId, orderId)
                    .last("LIMIT 1"));
            vo.setHelperStars(evaluation == null ? null : evaluation.getStars());
        }
        vo.setConfirmInitiator(order.getConfirmInitiator());
        vo.setCancelPendingBy(order.getCancelPendingBy());
        vo.setCanAppeal(appealService.canAppeal(order, uid));
        vo.setRequester(toRequester(profile));
        vo.setMine(mine);
        // 地址分级：远程类不返回地址；门牌永不返回；小区/区域对发布者本人也返回（修改页只读展示，D53）
        boolean remote = OrderType.REMOTE.name().equals(order.getOrderType());
        vo.setAddressArea(remote ? null : order.getAddressArea());
        if (!mine) {
            vo.setDistanceKm(distanceKm(defaultAddress(uid), order));
        }
        if (mine) {
            int views = order.getViewCount() == null ? 0 : order.getViewCount();
            vo.setViewTip("已有 " + views + " 位邻居看过您的求助");
        }
        return vo;
    }

    /** O5 我的求助 */
    public PageVO<MyOrderVO> mine(Long uid, String tab, Long page, Long pageSize) {
        // tab: DOING 进行中 / DONE 已了结 / HELPING 我接的单（全部状态，按接单者过滤，见 D16）
        boolean helping = "HELPING".equals(tab);
        List<String> statuses = "DONE".equals(tab)
                ? List.of(OrderStatus.COMPLETED.name(), OrderStatus.CANCELLED.name(), OrderStatus.EXPIRED.name())
                : List.of(OrderStatus.PENDING.name(), OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name(),
                OrderStatus.PENDING_CONFIRM.name(), OrderStatus.APPEALING.name());

        LambdaQueryWrapper<HelpOrder> wrapper = Wrappers.<HelpOrder>lambdaQuery()
                .orderByDesc(HelpOrder::getCreatedAt);
        if (helping) {
            wrapper.eq(HelpOrder::getHelperId, uid);
        } else {
            wrapper.eq(HelpOrder::getRequesterId, uid).in(HelpOrder::getStatus, statuses);
        }
        Page<HelpOrder> result = helpOrderMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);

        Map<Long, UserProfile> helpers = loadProfiles(result.getRecords().stream()
                .map(HelpOrder::getHelperId).filter(java.util.Objects::nonNull).toList());
        Map<Long, UserProfile> requesters = helping
                ? loadProfiles(result.getRecords().stream().map(HelpOrder::getRequesterId).toList())
                : Map.of();
        return PageVO.of(result.convert(order -> {
            MyOrderVO vo = new MyOrderVO();
            vo.setId(order.getId());
            vo.setOrderType(order.getOrderType());
            vo.setTitle(order.getTitle());
            vo.setTags(JsonUtils.toStringList(order.getTags()));
            vo.setStatus(order.getStatus());
            vo.setUrgentFlag(order.getUrgentFlag());
            vo.setViewCount(order.getViewCount());
            vo.setCurrentRound(order.getCurrentRound());
            vo.setCreatedAt(order.getCreatedAt());
            UserProfile helper = order.getHelperId() == null ? null : helpers.get(order.getHelperId());
            vo.setHelperNickname(helper == null ? null : helper.getNickname());
            UserProfile requester = requesters.get(order.getRequesterId());
            vo.setRequesterNickname(requester == null ? null : requester.getNickname());
            vo.setAppealDeadline(order.getAppealDeadline());
            return vo;
        }));
    }

    private HelpOrder buildOrder(Long uid, User user, UserProfile profile, String source, OrderType orderType,
                                 int urgentFlag, String title, String description, List<String> tags,
                                 String timeExpect, String addressArea, String addressDetail,
                                 Double addressLat, Double addressLng, String extraTag) {
        int urgencyBase = configService.getInt(orderType.urgencyBaseConfigKey(), 1);
        int urgencyScore = urgencyBase + urgentFlag * configService.getInt("urgency.urgent_weight", 3);
        Integer age = profile == null ? null : profile.getAge();
        int agePriority = age != null && age >= configService.getInt("elder.age_threshold", 60) ? 1 : 0;
        // 待响应截止跟随「希望什么时候」：尽快/急 6h，今天内到当天 24:00，明天到次日，本周到下周日，不限按僵尸清理天数
        LocalDateTime publishDeadline = PublishDeadlinePolicy.resolve(
                timeExpect,
                urgentFlag,
                configService.getInt("order.pending_timeout_urgent_hours", 6),
                configService.getInt("order.pending_timeout_hours", 24),
                configService.getInt("order.zombie_clean_days", 7));

        HelpOrder order = new HelpOrder();
        order.setRequesterId(uid);
        order.setSource(source);
        order.setOrderType(orderType.name());
        order.setUrgentFlag(urgentFlag);
        order.setUrgencyBase(urgencyBase);
        order.setUrgencyScore(urgencyScore);
        order.setTitle(title);
        order.setDescription(description);
        order.setAddressArea(addressArea);
        order.setAddressDetail(addressDetail);
        order.setAddressLat(addressLat);
        order.setAddressLng(addressLng);
        order.setTags(JsonUtils.toJson(tags));
        order.setExtraTag(extraTag);
        order.setTimeExpect(timeExpect);
        order.setStatus(OrderStatus.PENDING.name());
        order.setCurrentRound(1);
        order.setReleaseCount(0);
        order.setAgePriority(agePriority);
        order.setViewCount(0);
        order.setPublishDeadline(publishDeadline);
        order.setVersion(0);
        return order;
    }

    /** 自定义标签归一：去空白、≤10 字、与受控词表重复则忽略 */
    private String normalizeExtraTag(String extraTag) {
        if (extraTag == null) {
            return null;
        }
        String value = extraTag.trim();
        if (value.isEmpty() || HelpTagConstants.isTag(value)) {
            return null;
        }
        return value.length() > 10 ? value.substring(0, 10) : value;
    }

    private void validateTitleAndDescription(String title, String description) {
        if (title == null || title.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (title.length() > configService.getInt("help.title_max_length", 20)) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        if (description != null
                && description.length() > configService.getInt("content.desc_max_length", 200)) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
    }

    private List<String> validateTags(List<String> tags) {
        List<String> result = tags == null ? List.of() : tags;
        if (result.size() > configService.getInt("help.tag_max", 3)) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        for (String tag : result) {
            if (!HelpTagConstants.isTag(tag)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        }
        return result;
    }

    /** 地址快照：上门取 area+detail，陪同仅 area（作集合点），远程不引用地址 */
    private AddressSnapshot resolveAddress(Long uid, OrderType orderType, Long addressId) {
        if (!orderType.requiresAddress()) {
            if (addressId != null) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            return new AddressSnapshot(null, null, null, null);
        }
        if (addressId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        AddressBook address = addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getId, addressId)
                .eq(AddressBook::getUserId, uid));
        if (address == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        String detail = orderType.hasDetailAddress() ? address.getDetailText() : null;
        return new AddressSnapshot(address.getAreaText(), detail, address.getLatitude(), address.getLongitude());
    }

    private HelpOrder requireOwnExpiredOrder(Long uid, Long orderId) {
        if (orderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null || !order.getRequesterId().equals(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!OrderStatus.EXPIRED.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return order;
    }

    /** 「再来一次」来源校验：本人 + 已了结（COMPLETED/CANCELLED/EXPIRED） */
    private HelpOrder requireOwnRepeatableOrder(Long uid, Long orderId) {
        if (orderId == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null || !order.getRequesterId().equals(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!List.of(OrderStatus.COMPLETED.name(), OrderStatus.CANCELLED.name(),
                OrderStatus.EXPIRED.name()).contains(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return order;
    }

    /** 上门/外出类求助必须已填紧急联系人姓名与电话（服务过程中的安全兜底） */
    private void requireEmergencyContact(UserProfile profile, OrderType orderType) {
        if (!orderType.requiresAddress()) {
            return;
        }
        if (profile == null
                || profile.getEmergencyContactName() == null || profile.getEmergencyContactName().isBlank()
                || profile.getEmergencyContactPhone() == null || profile.getEmergencyContactPhone().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, EMERGENCY_CONTACT_MESSAGE);
        }
    }

    private void requirePendingLimit(Long uid) {
        Long pendingCount = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, uid)
                .eq(HelpOrder::getStatus, OrderStatus.PENDING.name()));
        if (pendingCount != null && pendingCount >= configService.getInt("order.pending_limit", 2)) {
            throw new BizException(ErrorCode.PENDING_LIMIT, PENDING_LIMIT_MESSAGE);
        }
    }

    private void touchInteraction(Long uid) {
        // 互动口径（详设 §4.2）：更新互动时间；RESTING 则 rest_since 重置（持久态不变）
        lightService.interaction(uid);
    }

    private boolean isNightTime() {
        int hour = LocalDateTime.now().getHour();
        return hour >= 22 || hour < 7;
    }

    /** 浏览去重：Redis 预检（TTL rate.browse_dedup_ttl_hours），真源为 order_view 唯一键 */
    private boolean markViewed(Long orderId, Long uid) {
        String key = BROWSE_DEDUP_KEY_PREFIX + orderId + ":" + uid;
        if (redisHealthProbe.isUsable()) {
            try {
                Duration ttl = Duration.ofHours(configService.getInt("rate.browse_dedup_ttl_hours", 24));
                Boolean absent = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
                if (Boolean.FALSE.equals(absent)) {
                    return false;
                }
            } catch (Exception e) {
                redisHealthProbe.markUnusable(e);
            }
        }
        try {
            OrderView view = new OrderView();
            view.setOrderId(orderId);
            view.setUserId(uid);
            orderViewMapper.insert(view);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private Map<Long, UserProfile> loadProfiles(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Map.of();
        }
        Map<Long, UserProfile> map = new HashMap<>();
        for (UserProfile profile : userProfileMapper.selectList(
                Wrappers.<UserProfile>lambdaQuery().in(UserProfile::getUserId, distinct))) {
            map.put(profile.getUserId(), profile);
        }
        return map;
    }

    private OrderCardVO toCard(HelpOrder order, UserProfile profile, Long uid, AddressBook myDefault) {
        OrderCardVO vo = new OrderCardVO();
        vo.setId(order.getId());
        vo.setTitle(order.getTitle());
        vo.setOrderType(order.getOrderType());
        vo.setTags(JsonUtils.toStringList(order.getTags()));
        vo.setExtraTag(order.getExtraTag());
        vo.setUrgentFlag(order.getUrgentFlag());
        vo.setTimeExpect(order.getTimeExpect());
        vo.setViewCount(order.getViewCount());
        vo.setCreatedAt(order.getCreatedAt());
        vo.setAddressArea(order.getAddressArea());
        vo.setRequester(toRequester(profile));
        vo.setMine(order.getRequesterId().equals(uid));
        vo.setDistanceKm(vo.isMine() ? null : distanceKm(myDefault, order));
        return vo;
    }

    private RequesterVO toRequester(UserProfile profile) {
        RequesterVO vo = new RequesterVO();
        vo.setId(profile == null ? null : profile.getUserId());
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        return vo;
    }

    private User requireUser(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private UserProfile findProfile(Long uid) {
        return userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, uid));
    }

    /** 本人默认地址（距离计算基准） */
    private AddressBook defaultAddress(Long uid) {
        AddressBook def = addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .eq(AddressBook::getIsDefault, 1)
                .last("LIMIT 1"));
        if (def != null) {
            return def;
        }
        // 4D-补：历史数据缺默认时兜底取最早一条，保证与前端展示口径一致
        return addressBookMapper.selectOne(Wrappers.<AddressBook>lambdaQuery()
                .eq(AddressBook::getUserId, uid)
                .orderByAsc(AddressBook::getId)
                .last("LIMIT 1"));
    }

    private boolean hasGeo(AddressBook address) {
        return address != null && address.getLatitude() != null && address.getLongitude() != null;
    }

    private Double distanceKm(AddressBook mine, HelpOrder order) {
        if (!hasGeo(mine) || order == null
                || order.getAddressLat() == null || order.getAddressLng() == null) {
            return null;
        }
        return haversineKm(mine.getLatitude(), mine.getLongitude(),
                order.getAddressLat(), order.getAddressLng());
    }

    /** 直线距离（公里，保留 2 位）；仅服务端使用，坐标不下发 */
    private Double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double earthRadius = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return Math.round(earthRadius * c * 100.0) / 100.0;
    }

    /** 距离相同/距离未知时的兜底排序：紧急度 → 年长 → 发布时间 */
    private int compareUrgency(HelpOrder a, HelpOrder b) {
        int urgencyA = a.getUrgencyScore() == null ? 0 : a.getUrgencyScore();
        int urgencyB = b.getUrgencyScore() == null ? 0 : b.getUrgencyScore();
        int cmp = Integer.compare(urgencyB, urgencyA);
        if (cmp != 0) {
            return cmp;
        }
        int ageA = a.getAgePriority() == null ? 0 : a.getAgePriority();
        int ageB = b.getAgePriority() == null ? 0 : b.getAgePriority();
        cmp = Integer.compare(ageB, ageA);
        if (cmp != 0) {
            return cmp;
        }
        return b.getCreatedAt().compareTo(a.getCreatedAt());
    }

    private record AddressSnapshot(String area, String detail, Double lat, Double lng) {
    }
}
