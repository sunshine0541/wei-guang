package com.weiguang.modules.report.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 防诈高危上报（表 report，仅管理员可见）。
 */
@Data
@TableName("report")
public class Report {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 证据消息 */
    private Long messageId;

    /** ORDER/POST/COMMENT（v1.3 内容举报） */
    private String targetType;

    /** 目标主键（v1.3；ORDER 时与 orderId 同值） */
    private Long targetId;

    /** 被上报人 */
    private Long targetUserId;

    /** 举报人（内容举报，v1.3） */
    private Long reporterId;

    /** FRAUD_RISK/CONTENT_REPORT */
    private String type;

    /** 脱敏后的命中要素与方向 */
    private String evidence;

    /** PENDING/CONFIRMED/REJECTED */
    private String status;

    /** 处理人 ID */
    private Long handlerId;

    /** 处理备注 */
    private String handleNote;

    /** NONE/CREDIT_MINUS/BAN */
    private String action;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 处理时间 */
    private LocalDateTime handledAt;
}
