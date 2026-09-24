package com.weiguang.modules.light.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 光语状态事件与徽记（表 light_event，只增不改不删）。
 */
@Data
@TableName("light_event")
public class LightEvent {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** STATE_CHANGE/BADGE_GAIN */
    private String eventType;

    /** 变更前状态 */
    private String fromState;

    /** 变更后状态 */
    private String toState;

    /** LIGHT_PASSED */
    private String badge;

    /** 关联订单 ID */
    private Long orderId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
