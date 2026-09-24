package com.weiguang.modules.order.service;

import com.weiguang.modules.order.entity.OrderTimeline;
import com.weiguang.modules.order.mapper.OrderTimelineMapper;
import org.springframework.stereotype.Component;

/**
 * 订单时间线记录器（详设 §2.8 只增不改）：状态变更与账本、通知同事务写入。
 */
@Component
public class OrderTimelineRecorder {

    private final OrderTimelineMapper orderTimelineMapper;

    public OrderTimelineRecorder(OrderTimelineMapper orderTimelineMapper) {
        this.orderTimelineMapper = orderTimelineMapper;
    }

    public void record(Long orderId, String eventType, Long operatorId, String remark) {
        OrderTimeline timeline = new OrderTimeline();
        timeline.setOrderId(orderId);
        timeline.setEventType(eventType);
        timeline.setOperatorId(operatorId == null ? 0L : operatorId);
        timeline.setRemark(remark);
        orderTimelineMapper.insert(timeline);
    }
}
