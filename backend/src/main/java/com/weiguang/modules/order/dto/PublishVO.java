package com.weiguang.modules.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * O2/O13 出参（详设 §3.3：{orderId, careNotice}）。
 */
@Data
@AllArgsConstructor
public class PublishVO {

    private Long orderId;

    /** 22:00–7:00 非急发布时为 true，前端弹温柔提示 */
    private boolean careNotice;
}
