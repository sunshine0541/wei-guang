package com.weiguang.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * O6 接单出参（详设 §3.3：{sessionId}）。
 */
@Data
@AllArgsConstructor
public class AcceptVO {

    private Long sessionId;
}
