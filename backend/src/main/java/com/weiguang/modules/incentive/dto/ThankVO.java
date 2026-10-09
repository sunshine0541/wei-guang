package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * U10 感谢墙单条（详设 §3.6：`[{letterId,orderId,title,content,senderNickname,createdAt}]`）。
 */
@Data
public class ThankVO {

    private Long letterId;
    private Long orderId;
    private String title;
    private String content;
    private String senderNickname;
    private LocalDateTime createdAt;
}