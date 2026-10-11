package com.weiguang.modules.incentive.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.incentive.dto.HomeSummaryVO;
import com.weiguang.modules.incentive.dto.HonorItemVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * H1 首页聚合（详设 §3.6 / §6.2 P03）：
 * - 荣耀榜 TOP10：近 30 天内完成结算（status=COMPLETED，仲裁改判取消的单已变 CANCELLED 自然剔除）、
 *   且未被仲裁改判为已取消的帮助次数；并列时按最近一次完成时间较早者优先；
 * - 善意回流计数：light_passed=1 的用户数（注销用户不计）。
 */
@Service
public class HonorService {

    /** 统计窗口（天）与榜单条数（详设 §3.6 H1、MVP 判据 15：TOP10） */
    private static final int WINDOW_DAYS = 30;
    private static final int TOP_N = 10;

    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;

    public HonorService(HelpOrderMapper helpOrderMapper,
                        UserMapper userMapper,
                        UserProfileMapper userProfileMapper) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
    }

    public HomeSummaryVO summary() {
        QueryWrapper<HelpOrder> aggregation = new QueryWrapper<>();
        aggregation.select("helper_id", "COUNT(*) AS help_count", "MAX(completed_at) AS last_finish")
                .eq("status", "COMPLETED")
                .ge("completed_at", LocalDateTime.now().minusDays(WINDOW_DAYS))
                .groupBy("helper_id")
                .orderByDesc("help_count")
                .orderByAsc("last_finish")
                .last("LIMIT " + TOP_N);
        List<Map<String, Object>> rows = helpOrderMapper.selectMaps(aggregation);

        HomeSummaryVO summary = new HomeSummaryVO();
        summary.setHonorTop10(buildTop(rows));
        summary.setLightPassedCount(userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getLightPassed, 1)
                .ne(User::getStatus, "CANCELLED")));
        return summary;
    }

    private List<HonorItemVO> buildTop(List<Map<String, Object>> rows) {
        List<Long> helperIds = rows.stream().map(row -> asLong(row.get("helper_id"))).toList();
        Map<Long, User> users = helperIds.isEmpty() ? Map.of()
                : userMapper.selectBatchIds(helperIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, UserProfile> profiles = helperIds.isEmpty() ? Map.of()
                : userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                                .in(UserProfile::getUserId, helperIds)).stream()
                        .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));

        List<HonorItemVO> top = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long helperId = asLong(row.get("helper_id"));
            User user = helperId == null ? null : users.get(helperId);
            if (user == null || "CANCELLED".equals(user.getStatus())) {
                // 注销用户不上榜（画像已匿名化）
                continue;
            }
            UserProfile profile = profiles.get(helperId);
            HonorItemVO item = new HonorItemVO();
            item.setNickname(profile == null ? null : profile.getNickname());
            item.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
            item.setHelpCount(asLong(row.get("help_count")));
            top.add(item);
        }
        return top;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}