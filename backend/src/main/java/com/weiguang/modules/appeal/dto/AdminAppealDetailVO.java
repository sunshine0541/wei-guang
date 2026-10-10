package com.weiguang.modules.appeal.dto;

import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.order.dto.TimelineVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * GET /admin/appeals/{id} 详情（详设 §3.4）：
 * 申诉=含订单、时间线、聊天记录（仅申诉关联时开放）；反馈=仅理由与结论。
 */
@Data
public class AdminAppealDetailVO {

    private Long id;
    private String type;
    private Long orderId;
    private String orderTitle;
    private String orderStatus;
    private String orderType;
    private Long requesterId;
    private Long helperId;
    private Long initiatorId;
    private String initiatorNickname;
    private String reason;
    private String status;
    private String result;
    private String conclusion;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
    /** 申诉关联时返回订单时间线；反馈为 null */
    private List<TimelineVO> timeline;
    /** 申诉关联时返回聊天记录（当前轮）；反馈为 null */
    private List<MessageVO> chatMessages;
}