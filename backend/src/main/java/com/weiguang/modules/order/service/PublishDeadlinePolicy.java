package com.weiguang.modules.order.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 待响应截止时间策略：按用户选择的「希望什么时候」计算，供发布（O2）、一键重发（O13）
 * 与释放重派（O8）共用。J1 定时任务据此判定是否超时。
 *
 * <p>「不限」按僵尸单清理天数（order.zombie_clean_days）兜底，避免长期占着待响应位。</p>
 */
public final class PublishDeadlinePolicy {

    private PublishDeadlinePolicy() {
    }

    public static LocalDateTime resolve(String timeExpect, int urgentFlag,
                                        int urgentHours, int normalHours, int zombieDays) {
        LocalDateTime now = LocalDateTime.now();
        if (urgentFlag == 1) {
            return now.plusHours(urgentHours);
        }
        String expect = timeExpect == null ? "" : timeExpect;
        LocalDateTime deadline;
        switch (expect) {
            case "ASAP":
                deadline = now.plusHours(urgentHours);
                break;
            case "TODAY":
                deadline = LocalDate.now().atTime(23, 59, 59);
                break;
            case "TOMORROW":
                deadline = LocalDate.now().plusDays(1).atTime(23, 59, 59);
                break;
            case "THIS_WEEK":
                deadline = LocalDate.now().with(DayOfWeek.SUNDAY).atTime(23, 59, 59);
                if (!deadline.isAfter(now)) {
                    deadline = deadline.plusWeeks(1);
                }
                break;
            case "ANY":
                deadline = now.plusDays(zombieDays);
                break;
            default:
                deadline = now.plusHours(normalHours);
                break;
        }
        // 兜底：避免算出的截止时间早于当前（例如 23:59:59 发布「今天内」）
        return deadline.isAfter(now) ? deadline : now.plusHours(1);
    }
}
