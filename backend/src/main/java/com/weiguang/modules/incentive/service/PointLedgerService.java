package com.weiguang.modules.incentive.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.incentive.entity.PointLedger;
import com.weiguang.modules.incentive.mapper.PointLedgerMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 积分明细的唯一入口：锁用户行后计算 balance_after，避免并发结算出现相同/跳号余额。
 * 唯一键冲突在方法内吞掉并返回 null，避免异常穿过事务代理把外层事务标记为 rollback-only。
 */
@Service
public class PointLedgerService {

    private final UserMapper userMapper;
    private final PointLedgerMapper pointLedgerMapper;

    public PointLedgerService(UserMapper userMapper, PointLedgerMapper pointLedgerMapper) {
        this.userMapper = userMapper;
        this.pointLedgerMapper = pointLedgerMapper;
    }

    /**
     * @param delta     有符号变化值（发放为正，追回为负）
     * @param uniqueKey 幂等键；重复时跳过
     * @return 变动后余额；重复跳过返回 null
     */
    @Transactional
    public Integer addIfAbsent(Long userId, int delta, String reasonCode, Long orderId, String uniqueKey) {
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery()
                .eq(User::getId, userId)
                .last("FOR UPDATE"));
        if (user == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        PointLedger latest = pointLedgerMapper.selectOne(Wrappers.<PointLedger>lambdaQuery()
                .eq(PointLedger::getUserId, userId)
                .orderByDesc(PointLedger::getId)
                // 锁定读：外层事务已建立快照，普通 SELECT 看不到并发事务刚提交的最新明细
                .last("LIMIT 1 FOR UPDATE"));
        int current = latest == null || latest.getBalanceAfter() == null ? 0 : latest.getBalanceAfter();
        int balance = Math.max(0, current + delta);

        PointLedger ledger = new PointLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setReasonCode(reasonCode);
        ledger.setOrderId(orderId);
        ledger.setUniqueKey(uniqueKey);
        ledger.setBalanceAfter(balance);
        ledger.setCreatedAt(LocalDateTime.now());
        try {
            pointLedgerMapper.insert(ledger);
            return balance;
        } catch (DuplicateKeyException e) {
            return null;
        }
    }
}
