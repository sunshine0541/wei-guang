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

    /** 微信 openid；注销时改写为 deleted_<user_id>（账密注册可为空） */
    private String openid;

    /** 登录账号名（账密登录） */
    private String username;

    /** BCrypt 密码散列 */
    private String passwordHash;

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

    /** UNLIT/LIT/STEADY（v1.3：只升不降；RESTING 已废弃） */
    private String lightState;

    /** 旧版"熄灭前状态"，v1.3 起停用（保留字段兼容历史数据） */
    private String lightPrevState;

    /** 旧版"熄灭时刻"，v1.3 起停用（保留字段兼容历史数据） */
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
