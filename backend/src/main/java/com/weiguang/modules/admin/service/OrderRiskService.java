package com.weiguang.modules.admin.service;

import com.weiguang.common.enums.OrderCategory;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * 4C 保底时长风险判定：只对已完成单，按「开始服务 → 标记完成」的实际用时，
 * 与该单标签映射到的分类保底值比较。用时低于保底 → 存疑，交由管理端人工核实。
 * 真实用时不含「等待对方确认」的时长（completed_at 不可用）。
 */
@Service
public class OrderRiskService {

    private final ConfigService configService;

    public OrderRiskService(ConfigService configService) {
        this.configService = configService;
    }

    public RiskInfo evaluate(HelpOrder order) {
        if (order == null
                || !OrderStatus.COMPLETED.name().equals(order.getStatus())
                || order.getStartedAt() == null
                || order.getCompleteRequestedAt() == null) {
            return RiskInfo.notEvaluated();
        }
        long minutes = Math.max(0,
                Duration.between(order.getStartedAt(), order.getCompleteRequestedAt()).toMinutes());
        List<OrderCategory> categories = OrderCategory.ofTags(JsonUtils.toStringList(order.getTags()));
        // 多标签时取最低保底，避免把「跑腿+家务」这类复合单误判
        int minMinutes = categories.stream()
                .mapToInt(category -> configService.getInt(category.configKey(), category.defaultMinMinutes()))
                .min()
                .orElse(OrderCategory.OTHER.defaultMinMinutes());
        List<String> labels = categories.stream().map(OrderCategory::label).toList();
        return new RiskInfo(true, minutes, minMinutes, labels, minutes < minMinutes);
    }

    public record RiskInfo(boolean evaluated, long durationMinutes, int minMinutes,
                           List<String> categories, boolean suspicious) {

        public static RiskInfo notEvaluated() {
            return new RiskInfo(false, 0, 0, List.of(), false);
        }
    }
}
