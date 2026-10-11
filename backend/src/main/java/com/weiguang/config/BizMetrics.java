package com.weiguang.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 业务计数器（详设 §1.2 config/BizMetrics）：注册到 Micrometer，经 /actuator/metrics 查询。
 */
@Component
public class BizMetrics {

    private final Counter orderAccept;
    private final Counter aiDegraded;

    public BizMetrics(MeterRegistry meterRegistry) {
        this.orderAccept = Counter.builder("weiguang.order.accept.total")
                .description("接单成功次数")
                .register(meterRegistry);
        this.aiDegraded = Counter.builder("weiguang.ai.degraded.total")
                .description("AI 降级次数（超时/失败/解析失败/合规不合格）")
                .register(meterRegistry);
    }

    /** O6 接单成功 +1 */
    public void orderAccepted() {
        orderAccept.increment();
    }

    /** AI 降级 +1（详设 §5.1） */
    public void aiDegraded() {
        aiDegraded.increment();
    }
}
