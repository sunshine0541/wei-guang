package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.modules.admin.dto.AdminOverviewVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /admin/overview 数据概览（详设 §3.4 / §6.3）：4 个数字。
 */
@Service
public class AdminOverviewService {

    private final UserMapper userMapper;
    private final HelpOrderMapper helpOrderMapper;

    public AdminOverviewService(UserMapper userMapper, HelpOrderMapper helpOrderMapper) {
        this.userMapper = userMapper;
        this.helpOrderMapper = helpOrderMapper;
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
        return vo;
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