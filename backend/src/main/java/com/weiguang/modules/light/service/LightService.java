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
import java.util.List;
import java.util.Set;

/**
 * 光语状态机（详设 §4.2 映射表，事件→状态无留白）：
 * - 持久状态：UNLIT/STEADY/RESTING 互斥；"被点亮"为推导态不落库；徽记 LIGHT_PASSED 永久叠加。
 * - 事件挂钩：建号（WxAuthService）/ 发布（OrderService）/ 结算与徽记（SettleService）/ U5（UserService）/
 *   连续被释放（ReleaseService，D15① 于 T15 落地）/ J7 扫描（本类）。
 * - 互动口径：仅更新 last_interaction_at；RESTING 则 rest_since 重置（小憩重新起算，持久态不变）。
 */
@Slf4j
@Service
public class LightService {

    public static final String STATE_UNLIT = "UNLIT";
    public static final String STATE_STEADY = "STEADY";
    public static final String STATE_RESTING = "RESTING";

    /** 推导态"被点亮"：存在已接单/服务中/待确认/申诉中的求助（详设 §4.2） */
    private static final Set<String> LIT_STATUSES = Set.of("ACCEPTED", "IN_SERVICE", "PENDING_CONFIRM", "APPEALING");

    /** 6.4 写死文案 */
    private static final String UNLIT_NAME = "未点亮";
    private static final String UNLIT_DESC = "您的微光还未点亮，需要的时候，邻居会为您而来。";
    private static final String LIT_NAME = "被点亮";
    private static final String LIT_DESC = "被点亮：已经有人在向您走来。";
    private static final String STEADY_NAME = "常亮";
    private static final String STEADY_DESC = "您的光正亮着，也在照亮别人。";
    private static final String RESTING_NAME = "小憩";
    private static final String RESTING_DESC = "您的光在小憩，养足精神再出发。";
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

    /** 互动口径（详设 §4.2 末行）：更新 last_interaction_at；RESTING 则将 rest_since 重置（持久态不变） */
    public void interaction(Long uid) {
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .set(User::getLastInteractionAt, LocalDateTime.now())
                .setSql("rest_since = IF(light_state = 'RESTING', NOW(), rest_since)"));
    }

    /** 发布求助（§4.2）：STEADY → RESTING（prev=STEADY、rest_since=now）+ 事件；UNLIT/RESTING 不变 */
    public void onPublish(Long uid) {
        toResting(uid, STATE_STEADY);
    }

    /** 连续被释放达标（§4.2，D15①）：STEADY 且计数 ≥ light.rest_release_count → RESTING + 事件 */
    public void onConsecutiveRelease(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null || !STATE_STEADY.equals(user.getLightState())) {
            return;
        }
        int threshold = configService.getInt("light.rest_release_count", 2);
        if (user.getConsecutiveReleaseCount() == null || user.getConsecutiveReleaseCount() < threshold) {
            return;
        }
        toResting(uid, STATE_STEADY);
    }

    /** 条件更新 STEADY→RESTING（幂等：WHERE light_state=旧值），成功则记事件 */
    private int toResting(Long uid, String fromState) {
        int updated = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, uid)
                .eq(User::getLightState, fromState)
                .set(User::getLightState, STATE_RESTING)
                .set(User::getLightPrevState, fromState)
                .set(User::getRestSince, LocalDateTime.now()));
        if (updated > 0) {
            recorder.record(LightEventRecorder.TYPE_STATE_CHANGE, uid, fromState, STATE_RESTING, null, null);
        }
        return updated;
    }

    // ---------- U7 查询 ----------

    /** U7 光状态：持久态+推导态+徽记+解释文案 6.4 + inviteDue（详设 §3.6 / §4.2 / P13） */
    public LightVO light(Long uid) {
        User user = userMapper.selectById(uid);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        boolean lit = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, uid)
                .in(HelpOrder::getStatus, LIT_STATUSES)) > 0;
        boolean passed = user.getLightPassed() != null && user.getLightPassed() == 1;

        LightVO vo = new LightVO();
        vo.setLightState(user.getLightState());
        if (lit) {
            vo.setLightStateName(LIT_NAME);
            vo.setLightStateDesc(LIT_DESC);
        } else {
            switch (user.getLightState() == null ? STATE_UNLIT : user.getLightState()) {
                case STATE_STEADY -> {
                    vo.setLightStateName(STEADY_NAME);
                    vo.setLightStateDesc(STEADY_DESC);
                }
                case STATE_RESTING -> {
                    vo.setLightStateName(RESTING_NAME);
                    vo.setLightStateDesc(RESTING_DESC);
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

    /** 邀请再触发（P13 Q13 规则）：非"常亮" + 曾被帮助 + 距上次弹窗 ≥ light.invite_interval_days */
    private boolean inviteDue(User user) {
        if (STATE_STEADY.equals(user.getLightState())) {
            return false;
        }
        if (user.getHelpedBefore() == null || user.getHelpedBefore() != 1) {
            return false;
        }
        int days = configService.getInt("light.invite_interval_days", 7);
        return user.getLastLightInviteAt() == null
                || !user.getLastLightInviteAt().isAfter(LocalDateTime.now().minusDays(days));
    }

    // ---------- J7 光语扫描（详设 §4.3，每 1 h） ----------

    /** ①STEADY 无互动超期或连续被释放达标 → RESTING；②RESTING 期满且期间无事件 → 恢复 prev_state */
    public void j7Scan() {
        LocalDateTime now = LocalDateTime.now();
        int triggerDays = configService.getInt("light.rest_trigger_days", 30);
        int restDays = configService.getInt("light.rest_duration_days", 7);
        int releaseThreshold = configService.getInt("light.rest_release_count", 2);

        int toRest = 0;
        int restored = 0;
        List<User> steadyUsers = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getLightState, STATE_STEADY));
        for (User user : steadyUsers) {
            boolean idle = user.getLastInteractionAt() == null
                    || user.getLastInteractionAt().isBefore(now.minusDays(triggerDays));
            boolean released = user.getConsecutiveReleaseCount() != null
                    && user.getConsecutiveReleaseCount() >= releaseThreshold;
            if (idle || released) {
                toRest += toResting(user.getId(), STATE_STEADY);
            }
        }
        List<User> restingUsers = userMapper.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getLightState, STATE_RESTING));
        for (User user : restingUsers) {
            // rest_since 为空视为异常数据，跳过；期中互动会重置 rest_since，故此处只需比对期满
            if (user.getRestSince() == null || user.getRestSince().isAfter(now.minusDays(restDays))) {
                continue;
            }
            String back = user.getLightPrevState() == null ? STATE_STEADY : user.getLightPrevState();
            int updated = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, user.getId())
                    .eq(User::getLightState, STATE_RESTING)
                    .set(User::getLightState, back)
                    .set(User::getRestSince, null)
                    .set(User::getLightPrevState, null));
            if (updated > 0) {
                recorder.record(LightEventRecorder.TYPE_STATE_CHANGE, user.getId(), STATE_RESTING, back, null, null);
                restored++;
            }
        }
        if (toRest > 0 || restored > 0) {
            log.info("J7 光语扫描：熄灭 {} 人、恢复 {} 人", toRest, restored);
        }
    }
}