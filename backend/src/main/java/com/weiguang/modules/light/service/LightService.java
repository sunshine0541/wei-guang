package com.weiguang.modules.light.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.light.dto.LightVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 光语 v2（详设 §15.3，替换 §4.2 旧三态）：
 * - 持久层级：UNLIT 未点亮 / LIT 被点亮过 / STEADY 常亮（已成为微光），只升不降。
 * - 派生标签：被点亮（存在进行中求助）、小憩（常亮且长期无互动或连续被释放）——不落库。
 * - 发布求助、被释放、取消、申诉均不改变层级；J7 只算标签，不再写库。
 * - 事件挂钩：建号（WxAuthService）/ 结算（SettleService）/ U5（UserService）/ 发布（OrderService 仅更新互动时间）。
 */
@Slf4j
@Service
public class LightService {

    public static final String STATE_UNLIT = "UNLIT";
    public static final String STATE_LIT = "LIT";
    public static final String STATE_STEADY = "STEADY";

    /** 推导标签"被点亮"：存在已接单/服务中/待确认/申诉中的求助 */
    private static final Set<String> LIT_STATUSES = Set.of("ACCEPTED", "IN_SERVICE", "PENDING_CONFIRM", "APPEALING");

    /** 6.4 写死文案（v1.3 修订） */
    private static final String UNLIT_NAME = "未点亮";
    private static final String UNLIT_DESC = "您的微光还未点亮，需要的时候，邻居会为您而来。";
    private static final String LIT_NAME = "被点亮";
    private static final String LIT_DESC = "被点亮：已经有人在向您走来。";
    private static final String LIT_LEVEL_NAME = "被点亮过";
    private static final String LIT_LEVEL_DESC = "您的光已经亮过，也记得为邻居留一盏。";
    private static final String STEADY_NAME = "常亮";
    private static final String STEADY_DESC = "您的光正亮着，也在照亮别人。";
    private static final String RESTING_NAME = "小憩";
    private static final String RESTING_DESC = "光在歇一会儿，回来时会更暖。";
    private static final String BADGE_DESC = "您曾被照亮，如今也照亮了别人——光已传递。";

    private final UserMapper userMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final ConfigService configService;
    private final LightEventRecorder recorder;

    public LightService(UserMapper userMapper, HelpOrderMapper helpOrderMapper,
                        ConfigService configService, LightEventRecorder recorder) {
        this.userMapper = userMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.configService = configService;
        this.recorder = recorder;
    }

    // ---------- 事件挂钩（供各业务模块调用） ----------

    /** 互动口径（详设 §15.3）：只更新 last_interaction_at，任何情况下不改层级 */
    public void interaction(Long uid) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getLastInteractionAt, LocalDateTime.now()));
    }

    /** 发布求助：仅更新互动时间（v1.3 起不再转"小憩"） */
    public void onPublish(Long uid) {
        interaction(uid);
    }

    // ---------- U7 查询 ----------

    /** U7 光状态：持久层级 + 派生标签（被点亮 / 小憩）+ 徽记 + 解释文案 + inviteDue */
    public LightVO light(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        String level = normalize(user.getLightState());
        boolean lit = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, uid)
                .in(HelpOrder::getStatus, LIT_STATUSES)) > 0;
        boolean passed = user.getLightPassed() != null && user.getLightPassed() == 1;

        LightVO vo = new LightVO();
        vo.setLightState(level);
        if (lit) {
            vo.setLightStateName(LIT_NAME);
            vo.setLightStateDesc(LIT_DESC);
        } else if (resting(user)) {
            vo.setLightStateName(RESTING_NAME);
            vo.setLightStateDesc(RESTING_DESC);
        } else {
            switch (level) {
                case STATE_STEADY -> {
                    vo.setLightStateName(STEADY_NAME);
                    vo.setLightStateDesc(STEADY_DESC);
                }
                case STATE_LIT -> {
                    vo.setLightStateName(LIT_LEVEL_NAME);
                    vo.setLightStateDesc(LIT_LEVEL_DESC);
                }
                default -> {
                    vo.setLightStateName(UNLIT_NAME);
                    vo.setLightStateDesc(UNLIT_DESC);
                }
            }
        }
        vo.setLit(lit);
        vo.setLightPassed(passed ? 1 : 0);
        vo.setBadgeDesc(passed ? BADGE_DESC : null);
        vo.setInviteDue(inviteDue(user));
        return vo;
    }

    /** 历史数据兼容：旧 RESTING 视为常亮（v1.3 只升不降，seed 重写时清理）；空值视为未点亮 */
    private String normalize(String state) {
        if (STATE_LIT.equals(state) || STATE_STEADY.equals(state)) {
            return state;
        }
        if (STATE_UNLIT.equals(state) || state == null) {
            return STATE_UNLIT;
        }
        return STATE_STEADY;
    }

    /** 小憩标签：常亮用户长期无互动或连续被释放达标（只影响展示，不写库） */
    private boolean resting(User user) {
        if (!STATE_STEADY.equals(normalize(user.getLightState()))) {
            return false;
        }
        int triggerDays = configService.getInt("light.rest_trigger_days", 30);
        int releaseThreshold = configService.getInt("light.rest_release_count", 2);
        boolean idle = user.getLastInteractionAt() == null
                || user.getLastInteractionAt().isBefore(LocalDateTime.now().minusDays(triggerDays));
        boolean released = user.getConsecutiveReleaseCount() != null
                && user.getConsecutiveReleaseCount() >= releaseThreshold;
        return idle || released;
    }

    /** 邀请再触发（P13 Q13 规则）：非常亮 + 曾被帮助 + 距上次弹窗 ≥ light.invite_interval_days */
    private boolean inviteDue(User user) {
        if (STATE_STEADY.equals(normalize(user.getLightState()))) {
            return false;
        }
        if (user.getHelpedBefore() == null || user.getHelpedBefore() != 1) {
            return false;
        }
        int days = configService.getInt("light.invite_interval_days", 7);
        return user.getLastLightInviteAt() == null
                || !user.getLastLightInviteAt().isAfter(LocalDateTime.now().minusDays(days));
    }
}
