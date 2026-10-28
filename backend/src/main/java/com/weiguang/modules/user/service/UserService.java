package com.weiguang.modules.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.constants.HelpTagConstants;
import com.weiguang.common.constants.ProfileConstants;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatSession;
import com.weiguang.modules.chat.mapper.ChatSessionMapper;
import com.weiguang.modules.light.service.LightEventRecorder;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.order.service.OrderTimelineRecorder;
import com.weiguang.modules.order.service.ReleaseService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.dto.ModeVO;
import com.weiguang.modules.user.dto.MotionVO;
import com.weiguang.modules.user.dto.ProfileUpdateRequest;
import com.weiguang.modules.user.dto.ProfileUpdateVO;
import com.weiguang.modules.user.dto.UserProfileVO;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.AddressBookMapper;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 用户域（详设 §3.3 U1–U6、§3.6 U3/U4、A3）。
 */
@Slf4j
@Service
public class UserService {

    private static final String MODE_NORMAL = "NORMAL";
    private static final String MODE_ELDER = "ELDER";
    private static final String STATE_STEADY = "STEADY";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String CHOICE_WILLING = "WILLING";
    private static final String CHOICE_LATER = "LATER";

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final AddressBookMapper addressBookMapper;
    private final ConfigService configService;
    private final LightEventRecorder lightEventRecorder;
    private final HelpOrderMapper helpOrderMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final OrderTimelineRecorder timelineRecorder;
    private final NotifyService notifyService;
    private final ReleaseService releaseService;
    private final WsSessionRegistry wsSessionRegistry;

    public UserService(UserMapper userMapper,
                       UserProfileMapper userProfileMapper,
                       AddressBookMapper addressBookMapper,
                       ConfigService configService,
                       LightEventRecorder lightEventRecorder,
                       HelpOrderMapper helpOrderMapper,
                       ChatSessionMapper chatSessionMapper,
                       OrderTimelineRecorder timelineRecorder,
                       NotifyService notifyService,
                       ReleaseService releaseService,
                       WsSessionRegistry wsSessionRegistry) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.addressBookMapper = addressBookMapper;
        this.configService = configService;
        this.lightEventRecorder = lightEventRecorder;
        this.helpOrderMapper = helpOrderMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.timelineRecorder = timelineRecorder;
        this.notifyService = notifyService;
        this.releaseService = releaseService;
        this.wsSessionRegistry = wsSessionRegistry;
    }

    /** U1 我的档案 */
    public UserProfileVO getProfile(Long uid) {
        User user = requireUser(uid);
        return toVO(user, findProfile(uid));
    }

    /** U2 更新画像；返回重算的帮助方向草稿供前端确认 */
    @Transactional
    public ProfileUpdateVO updateProfile(Long uid, ProfileUpdateRequest request) {
        User user = requireUser(uid);
        UserProfile profile = findProfile(uid);
        if (profile == null) {
            profile = new UserProfile();
            profile.setUserId(uid);
        }

        // D52：紧急联系人姓名与电话必须成对；仅在请求涉及联系人字段时校验，不影响无关资料更新
        if (request.getEmergencyContactName() != null || request.getEmergencyContactPhone() != null) {
            String effectiveName = request.getEmergencyContactName() != null
                    ? request.getEmergencyContactName() : profile.getEmergencyContactName();
            String effectivePhone = request.getEmergencyContactPhone() != null
                    ? request.getEmergencyContactPhone() : profile.getEmergencyContactPhone();
            boolean hasName = effectiveName != null && !effectiveName.isBlank();
            boolean hasPhone = effectivePhone != null && !effectivePhone.isBlank();
            if (hasName != hasPhone) {
                throw new BizException(ErrorCode.PARAM_INVALID, "紧急联系人的姓名和电话要一起填");
            }
        }

        if (request.getHobbies() != null) {
            requireAllHobbies(request.getHobbies());
            profile.setHobbies(JsonUtils.toJson(request.getHobbies()));
        }
        if (request.getHelpDirections() != null) {
            requireAllTags(request.getHelpDirections());
            profile.setHelpDirections(JsonUtils.toJson(dedupe(request.getHelpDirections())));
        }
        if (request.getNickname() != null) {
            profile.setNickname(request.getNickname());
        }
        if (request.getAvatarUrl() != null) {
            profile.setAvatarUrl(request.getAvatarUrl());
        }
        if (request.getAge() != null) {
            profile.setAge(request.getAge());
        }
        if (request.getSignature() != null) {
            profile.setSignature(request.getSignature());
        }
        if (request.getCareTip() != null) {
            profile.setCareTip(request.getCareTip());
        }
        if (request.getEmergencyContactName() != null) {
            profile.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            profile.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        if (request.getPhone() != null) {
            profile.setPhone(request.getPhone());
        }

        if (profile.getId() == null) {
            userProfileMapper.insert(profile);
        } else {
            userProfileMapper.updateById(profile);
        }

        List<String> draft = HelpTagConstants.directionsOf(JsonUtils.toStringList(profile.getHobbies()));
        return new ProfileUpdateVO(toVO(user, profile), draft);
    }

    /** U3 切换模式：动效重置为该模式默认值（普通=1，年长=0） */
    public ModeVO switchMode(Long uid, String uiMode) {
        if (!MODE_NORMAL.equals(uiMode) && !MODE_ELDER.equals(uiMode)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        requireUser(uid);
        int motionEnabled = MODE_ELDER.equals(uiMode) ? 0 : 1;
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getUiMode, uiMode)
                .set(User::getMotionEnabled, motionEnabled));
        return new ModeVO(uiMode, motionEnabled);
    }

    /** U4 动效开关（独立于模式） */
    public MotionVO switchMotion(Long uid, Integer enabled) {
        if (enabled == null || (enabled != 0 && enabled != 1)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        requireUser(uid);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getMotionEnabled, enabled));
        return new MotionVO(enabled);
    }

    /** U5 微光邀请应答：WILLING 且当前非常亮 → 常亮 + 按映射写帮助方向；两种选择均记录时间戳 */
    @Transactional
    public void lightInvite(Long uid, String choice) {
        if (!CHOICE_WILLING.equals(choice) && !CHOICE_LATER.equals(choice)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        User user = requireUser(uid);

        boolean toSteady = CHOICE_WILLING.equals(choice) && !STATE_STEADY.equals(user.getLightState());
        var update = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getInviteFlag, choice)
                .set(User::getLastLightInviteAt, LocalDateTime.now());
        if (toSteady) {
            update.set(User::getLightState, STATE_STEADY);
        }
        userMapper.update(null, update);

        if (toSteady) {
            writeMappedDirections(uid);
            // 光语映射（详设 §4.2）：选择"我愿意成为微光" → STEADY + 事件
            lightEventRecorder.record(LightEventRecorder.TYPE_STATE_CHANGE, uid,
                    user.getLightState() == null ? "UNLIT" : user.getLightState(), STATE_STEADY, null, null);
        }
    }

    /** U6 注销账号：先了结进行中求助，再匿名化（openid/username 改写、画像清空、地址物理删除、status=CANCELLED） */
    @Transactional
    public void cancelAccount(Long uid) {
        User user = requireUser(uid);
        LocalDateTime now = LocalDateTime.now();
        settleInProgressOrders(uid, now);

        var update = Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getStatus, STATUS_CANCELLED);
        if (user.getOpenid() != null) {
            update.set(User::getOpenid, "deleted_" + uid);
        }
        if (user.getUsername() != null) {
            update.set(User::getUsername, "deleted_" + uid);
        }
        userMapper.update(null, update);
        userProfileMapper.update(null, Wrappers.<UserProfile>lambdaUpdate()
                .eq(UserProfile::getUserId, uid)
                .set(UserProfile::getNickname, ProfileConstants.CANCELLED_NICKNAME)
                .set(UserProfile::getAvatarUrl, null)
                .set(UserProfile::getAge, null)
                .set(UserProfile::getSignature, null)
                .set(UserProfile::getHobbies, null)
                .set(UserProfile::getCareTip, null)
                .set(UserProfile::getHelpDirections, null)
                .set(UserProfile::getEmergencyContactName, null)
                .set(UserProfile::getEmergencyContactPhone, null)
                .set(UserProfile::getPhone, null));
        int removed = addressBookMapper.physicalDeleteByUserId(uid);
        log.info("账号注销完成 uid={}，地址簿物理删除 {} 条", uid, removed);
    }

    /**
     * 注销前了结进行中求助（详设 §15.6 / 执行文档 T20）：
     * 作为求助者：PENDING/ACCEPTED/IN_SERVICE → CANCELLED 并通知接单者；
     * 作为助人者：ACCEPTED/IN_SERVICE → 免罚释放回求助池（不扣微光值）；
     * PENDING_CONFIRM/APPEALING 保留，由 J3 超时或管理员仲裁自然了结。
     */
    private void settleInProgressOrders(Long uid, LocalDateTime now) {
        List<HelpOrder> asRequester = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, uid)
                .in(HelpOrder::getStatus, OrderStatus.PENDING.name(),
                        OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name()));
        for (HelpOrder order : asRequester) {
            boolean fromPending = OrderStatus.PENDING.name().equals(order.getStatus());
            int rows = helpOrderMapper.update(null, Wrappers.<HelpOrder>lambdaUpdate()
                    .eq(HelpOrder::getId, order.getId())
                    .eq(HelpOrder::getStatus, order.getStatus())
                    .set(HelpOrder::getStatus, OrderStatus.CANCELLED.name())
                    .set(HelpOrder::getCancelBy, "REQUESTER")
                    .set(HelpOrder::getCancelReason, "账号注销")
                    .set(HelpOrder::getCancelledAt, now)
                    .set(HelpOrder::getAppealDeadline, fromPending ? null
                            : now.plusDays(configService.getInt("order.appeal_window_days", 7)))
                    .set(HelpOrder::getCancelPendingBy, null)
                    .set(HelpOrder::getCancelPendingDeadline, null));
            if (rows == 0) {
                continue;
            }
            closeCurrentSession(order, now);
            timelineRecorder.record(order.getId(), "CANCEL", uid, "账号注销");
            Long helperId = order.getHelperId();
            if (helperId != null) {
                notifyService.cancelNotice(helperId, order.getTitle(), order.getId());
                wsSessionRegistry.push(helperId,
                        WsEnvelope.orderStatus(order.getId(), OrderStatus.CANCELLED.name(), order.getTitle()));
            }
        }

        List<HelpOrder> asHelper = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getHelperId, uid)
                .in(HelpOrder::getStatus, OrderStatus.ACCEPTED.name(), OrderStatus.IN_SERVICE.name()));
        for (HelpOrder order : asHelper) {
            // 免罚释放：账号注销不属于违约，回到待响应池由其他邻居接续
            releaseService.coreRelease(order, "ACCOUNT_CANCELLED", uid, "对方账号注销", "NONE");
        }
    }

    private void closeCurrentSession(HelpOrder order, LocalDateTime now) {
        int round = order.getCurrentRound() == null ? 1 : order.getCurrentRound();
        chatSessionMapper.update(null, Wrappers.<ChatSession>lambdaUpdate()
                .eq(ChatSession::getOrderId, order.getId())
                .eq(ChatSession::getRound, round)
                .eq(ChatSession::getStatus, "ACTIVE")
                .set(ChatSession::getStatus, "CLOSED")
                .set(ChatSession::getClosedAt, now));
    }

    /** A3 隐私同意：版本必须与当前 privacy.policy_version 一致 */
    @Transactional
    public void privacyAgree(Long uid, String version) {
        String currentVersion = configService.get("privacy.policy_version", "v1.0");
        if (!currentVersion.equals(version)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        requireUser(uid);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getPrivacyVersion, version)
                .set(User::getPrivacyAgreedAt, LocalDateTime.now()));
    }

    private void writeMappedDirections(Long uid) {
        UserProfile profile = findProfile(uid);
        if (profile == null) {
            return;
        }
        List<String> mapped = HelpTagConstants.directionsOf(JsonUtils.toStringList(profile.getHobbies()));
        if (mapped.isEmpty()) {
            return;
        }
        LinkedHashSet<String> merged = new LinkedHashSet<>(JsonUtils.toStringList(profile.getHelpDirections()));
        merged.addAll(mapped);
        profile.setHelpDirections(JsonUtils.toJson(List.copyOf(merged)));
        userProfileMapper.updateById(profile);
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

    private void requireAllHobbies(List<String> hobbies) {
        for (String hobby : hobbies) {
            if (!HelpTagConstants.isHobby(hobby)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        }
    }

    private void requireAllTags(List<String> directions) {
        for (String direction : directions) {
            if (!HelpTagConstants.isTag(direction)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        }
    }

    private List<String> dedupe(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private UserProfileVO toVO(User user, UserProfile profile) {
        UserProfileVO vo = new UserProfileVO();
        vo.setUserId(user.getId());
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        vo.setAge(profile == null ? null : profile.getAge());
        vo.setSignature(profile == null ? null : profile.getSignature());
        vo.setHobbies(profile == null ? List.of() : JsonUtils.toStringList(profile.getHobbies()));
        vo.setCareTip(profile == null ? null : profile.getCareTip());
        vo.setHelpDirections(profile == null ? List.of() : JsonUtils.toStringList(profile.getHelpDirections()));
        vo.setEmergencyContactName(profile == null ? null : profile.getEmergencyContactName());
        vo.setEmergencyContactPhone(profile == null ? null : profile.getEmergencyContactPhone());
        vo.setPhone(profile == null ? null : profile.getPhone());
        vo.setUiMode(user.getUiMode());
        vo.setMotionEnabled(user.getMotionEnabled());
        vo.setCreditScore(user.getCreditScore());
        vo.setCreditThreshold(configService.getInt("credit.threshold", 60));
        vo.setCoolingDown(user.getCooldownUntil() != null
                && user.getCooldownUntil().isAfter(java.time.LocalDateTime.now()));
        vo.setLightState(user.getLightState());
        vo.setLightPassed(user.getLightPassed());
        vo.setHasPrivacyAgreed(configService.get("privacy.policy_version", "v1.0").equals(user.getPrivacyVersion()));
        vo.setRetentionDays(configService.getInt("compliance.retention_days", 180));
        vo.setElderAgeThreshold(configService.getInt("elder.age_threshold", 60));
        vo.setPrivacyVersion(configService.get("privacy.policy_version", "v1.0"));
        return vo;
    }
}
