package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.admin.dto.AdminOverviewVO;
import com.weiguang.modules.community.entity.CommunityPost;
import com.weiguang.modules.community.mapper.CommunityPostMapper;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /admin/overview 数据概览（详设 §3.4 / §6.3）：基础数字 + 近 7 天趋势 + 状态分布 + 安全统计。
 */
@Service
public class AdminOverviewService {

    private static final int TREND_DAYS = 7;

    private final UserMapper userMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final ReportMapper reportMapper;
    private final CommunityPostMapper communityPostMapper;

    public AdminOverviewService(UserMapper userMapper, HelpOrderMapper helpOrderMapper,
                                ReportMapper reportMapper, CommunityPostMapper communityPostMapper) {
        this.userMapper = userMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.reportMapper = reportMapper;
        this.communityPostMapper = communityPostMapper;
    }

    public AdminOverviewVO overview() {
        AdminOverviewVO vo = new AdminOverviewVO();
        vo.setUserCount(userMapper.selectCount(null));
        vo.setOrderTotal(helpOrderMapper.selectCount(null));
        vo.setCompletedCount(helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, "COMPLETED")));

        Map<String, Long> distribution = new LinkedHashMap<>();
        distribution.put("HOME_VISIT", 0L);
        distribution.put("ACCOMPANY", 0L);
        distribution.put("REMOTE", 0L);
        QueryWrapper<HelpOrder> aggregation = new QueryWrapper<>();
        aggregation.select("order_type", "COUNT(*) AS cnt").groupBy("order_type");
        for (Map<String, Object> row : helpOrderMapper.selectMaps(aggregation)) {
            String type = String.valueOf(row.get("order_type"));
            if (distribution.containsKey(type)) {
                distribution.put(type, asLong(row.get("cnt")));
            }
        }
        vo.setTypeDistribution(distribution);

        Map<String, Long> statuses = new LinkedHashMap<>();
        QueryWrapper<HelpOrder> statusAgg = new QueryWrapper<>();
        statusAgg.select("status", "COUNT(*) AS cnt").groupBy("status");
        for (Map<String, Object> row : helpOrderMapper.selectMaps(statusAgg)) {
            statuses.put(String.valueOf(row.get("status")), asLong(row.get("cnt")));
        }
        vo.setStatusDistribution(statuses);

        vo.setLast7Days(buildTrend());
        vo.setFraudReportCount(reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getType, "FRAUD_RISK")));
        vo.setPendingManualPostCount(communityPostMapper.selectCount(Wrappers.<CommunityPost>lambdaQuery()
                .eq(CommunityPost::getStatus, "PENDING_MANUAL")));
        return vo;
    }

    private List<AdminOverviewVO.DailyTrendVO> buildTrend() {
        LocalDate start = LocalDate.now().minusDays(TREND_DAYS - 1);
        Map<String, Long> orders = countByDate(helpOrderMapper.selectMaps(new QueryWrapper<HelpOrder>()
                .select("DATE(created_at) AS d", "COUNT(*) AS cnt")
                .ge("created_at", start.atStartOfDay())
                .groupBy("DATE(created_at)")));
        Map<String, Long> completed = countByDate(helpOrderMapper.selectMaps(new QueryWrapper<HelpOrder>()
                .select("DATE(completed_at) AS d", "COUNT(*) AS cnt")
                .ge("completed_at", start.atStartOfDay())
                .groupBy("DATE(completed_at)")));
        Map<String, Long> users = countByDate(userMapper.selectMaps(new QueryWrapper<User>()
                .select("DATE(created_at) AS d", "COUNT(*) AS cnt")
                .ge("created_at", start.atStartOfDay())
                .groupBy("DATE(created_at)")));

        List<AdminOverviewVO.DailyTrendVO> trend = new ArrayList<>();
        for (int i = 0; i < TREND_DAYS; i++) {
            String date = start.plusDays(i).toString();
            AdminOverviewVO.DailyTrendVO item = new AdminOverviewVO.DailyTrendVO();
            item.setDate(date);
            item.setOrderCount(orders.getOrDefault(date, 0L));
            item.setCompletedCount(completed.getOrDefault(date, 0L));
            item.setUserCount(users.getOrDefault(date, 0L));
            trend.add(item);
        }
        return trend;
    }

    private Map<String, Long> countByDate(List<Map<String, Object>> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            Object date = row.get("d");
            if (date == null) {
                continue;
            }
            String key = date.toString();
            if (key.length() > 10) {
                key = key.substring(0, 10);
            }
            map.put(key, asLong(row.get("cnt")));
        }
        return map;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
