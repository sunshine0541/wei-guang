package com.weiguang.modules.admin.dto;

import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.order.dto.TimelineVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /admin/orders/{id} 详情（详设 §3.4）：状态时间线、参与双方；
 * 聊天记录仅当该单存在申诉或高危上报关联时返回；协商取消裁决仅挂起单可用。
 */
@Data
public class AdminOrderDetailVO {

    private Long id;
    private String title;
    private String description;
    private String orderType;
    private String status;
    private Integer urgentFlag;
    private Integer urgencyScore;
    private List<String> tags;
    private String timeExpect;
    private String addressArea;
    private String addressDetail;
    private Integer viewCount;
    private Integer currentRound;
    private Integer releaseCount;
    private LocalDateTime createdAt;
    private LocalDateTime publishDeadline;
    private LocalDateTime acceptedAt;
    private LocalDateTime startedAt;
    private String confirmInitiator;
    private LocalDateTime confirmDeadline;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
    private String cancelBy;
    private String cancelReason;
    private String cancelPendingBy;
    private LocalDateTime cancelPendingDeadline;
    private LocalDateTime cancelArbitrateAt;
    private LocalDateTime appealDeadline;

    /** 4C 保底时长风险字段 */
    private LocalDateTime completeRequestedAt;
    private Long durationMinutes;
    private Integer minMinutes;
    private List<String> riskCategories;
    private Boolean suspicious;
    private boolean riskReviewAvailable;
    private LocalDateTime riskReviewedAt;
    private String riskReviewResult;
    private String riskReviewNote;

    private AdminUserBriefVO requester;
    private AdminUserBriefVO helper;

    private List<TimelineVO> timeline;
    /** 聊天记录按钮可用性：存在申诉或高危上报关联 */
    private boolean chatAvailable;
    private List<MessageVO> chatMessages;
    /** 协商取消裁决操作区：仅挂起单（cancel_pending_by 非空且未完成态）可用 */
    private boolean cancelArbitrateAvailable;
}