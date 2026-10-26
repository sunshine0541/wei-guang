package com.weiguang.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求助单（表 help_order）。
 */
@Data
@TableName("help_order")
public class HelpOrder {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 发布者 */
    private Long requesterId;

    /** 当前轮接单者 */
    private Long helperId;

    /** TEMPLATE/FREE/REPEAT */
    private String source;

    /** HOME_VISIT/ACCOMPANY/REMOTE */
    private String orderType;

    /** 紧急标记 */
    private Integer urgentFlag;

    /** 类型基准分 */
    private Integer urgencyBase;

    /** 紧急度总分 */
    private Integer urgencyScore;

    /** 标题 */
    private String title;

    /** 描述 */
    private String description;

    /** 地址快照-小区/区域 */
    private String addressArea;

    /** 地址快照-楼栋门牌（仅上门类可有值） */
    private String addressDetail;

    /** 地址快照-纬度（服务端距离计算，不下发） */
    private Double addressLat;

    /** 地址快照-经度（服务端距离计算，不下发） */
    private Double addressLng;

    /** JSON 数组，受控词表，<=3 个 */
    private String tags;
    /** 自定义标签（仅展示，不入推荐） */
    private String extraTag;

    /** ASAP/TODAY/TOMORROW/THIS_WEEK/ANY */
    private String timeExpect;

    /** PENDING/ACCEPTED/IN_SERVICE/PENDING_CONFIRM/COMPLETED/CANCELLED/EXPIRED/APPEALING */
    private String status;

    /** 进入申诉前状态 */
    private String preAppealStatus;

    /** 派单轮次 */
    private Integer currentRound;

    /** 被释放次数 */
    private Integer releaseCount;

    /** 年长排序键；只参与 SQL 排序，禁止出现在任何 API 响应 */
    private Integer agePriority;

    /** 浏览计数 */
    private Integer viewCount;

    /** 待响应截止 */
    private LocalDateTime publishDeadline;

    /** 接单时间 */
    private LocalDateTime acceptedAt;

    /** 确认开始截止 */
    private LocalDateTime startDeadline;

    /** 开始服务时间 */
    private LocalDateTime startedAt;

    /** REQUESTER/HELPER */
    private String confirmInitiator;

    /** 待确认截止 */
    private LocalDateTime confirmDeadline;

    /** 完成时间 */
    private LocalDateTime completedAt;

    /** 标记完成时间（O11；风险判定用，不含确认等待） */
    private LocalDateTime completeRequestedAt;

    /** 取消时间 */
    private LocalDateTime cancelledAt;

    /** REQUESTER/HELPER/ADMIN */
    private String cancelBy;

    /** 取消原因 */
    private String cancelReason;

    /** 协商取消发起方 */
    private String cancelPendingBy;

    /** 协商取消响应截止 */
    private LocalDateTime cancelPendingDeadline;

    /** 协商取消裁决时间（CANCEL/KEEP 均写） */
    private LocalDateTime cancelArbitrateAt;

    /** 申诉截止 */
    private LocalDateTime appealDeadline;

    /** 风险核实时间 */
    private LocalDateTime riskReviewedAt;

    /** 风险核实结果：NORMAL/DEDUCT */
    private String riskReviewResult;

    /** 风险核实备注 */
    private String riskReviewNote;

    /** MyBatis-Plus @Version 乐观锁 */
    @Version
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
