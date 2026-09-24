package com.weiguang.modules.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 账号主体（表 user）。
 */
@Data
@TableName("user")
public class User {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 微信 openid；注销时改写为 deleted_<user_id> */
    private String openid;

    /** NORMAL 普通 / ELDER 年长 */
    private String uiMode;

    /** 动效开关 */
    private Integer motionEnabled;

    /** 微光值，下限 0 */
    private Integer creditScore;

    /** 违约累计计数，冷却结束清零 */
    private Integer violationCount;

    /** 接单冷却截止 */
    private LocalDateTime cooldownUntil;

    /** NORMAL/BANNED/CANCELLED */
    private String status;

    /** UNLIT/STEADY/RESTING */
    private String lightState;

    /** 进入熄灭前的持久状态 */
    private String lightPrevState;

    /** 进入熄灭时刻 */
    private LocalDateTime restSince;

    /** 光已传递徽记 */
    private Integer lightPassed;

    /** 是否曾被帮助完成 */
    private Integer helpedBefore;

    /** 连续被释放计数（求助者视角） */
    private Integer consecutiveReleaseCount;

    /** 最近互动时间 */
    private LocalDateTime lastInteractionAt;

    /** 最近微光邀请弹窗时间 */
    private LocalDateTime lastLightInviteAt;

    /** WILLING/LATER/NULL */
    private String inviteFlag;

    /** 已同意的隐私说明版本号 */
    private String privacyVersion;

    /** 同意时间 */
    private LocalDateTime privacyAgreedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
