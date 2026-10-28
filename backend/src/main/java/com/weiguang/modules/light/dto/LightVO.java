package com.weiguang.modules.light.dto;

import lombok.Data;

/**
 * U7 GET /api/user/light 响应（详设 §3.6）：
 * {lightState, lightStateName, lightStateDesc, lit（推导态被点亮 bool), lightPassed, badgeDesc}
 * + inviteDue（微光邀请再触发判定，详设 P13 Q13 规则；见《实现偏差清单》D26）。
 * 说明：lit=true 时 lightStateName/Desc 返回"被点亮"展示文案（推导态不落库，详设 §4.2）。
 */
@Data
public class LightVO {

    /** 持久层级：UNLIT 未点亮 / LIT 被点亮过 / STEADY 常亮 */
    private String lightState;

    /** 展示名称（6.4 文案；lit=true 时为"被点亮"） */
    private String lightStateName;

    /** 一句话解释（6.4 文案） */
    private String lightStateDesc;

    /** 推导态被点亮：存在已接单/服务中/待确认/申诉中的求助 */
    private boolean lit;

    /** 光已传递徽记：0/1 */
    private Integer lightPassed;

    /** 徽记解释（未获得为 null） */
    private String badgeDesc;

    /** 微光邀请再触发：非"常亮"且曾被帮助且距上次弹窗 ≥ light.invite_interval_days */
    private boolean inviteDue;
}