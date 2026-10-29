package com.weiguang.modules.incentive.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 微光值变动的唯一入口：原子 SQL 收敛下限 + 同事务回读余额 + 写明细。
 * 避免各业务各自「读-改-写」在并发下互相覆盖。
 */
@Service
public class CreditService {

    private final UserMapper userMapper;
    private final CreditLedgerMapper creditLedgerMapper;

    public CreditService(UserMapper userMapper, CreditLedgerMapper creditLedgerMapper) {
        this.userMapper = userMapper;
        this.creditLedgerMapper = creditLedgerMapper;
    }

    /**
     * @param delta      有符号变化值（正加负减）
     * @param operatorId 0=系统；管理员处置时为 admin_user.id
     * @param remark     可选备注
     * @return 变动后的微光值
     */
    @Transactional
    public int apply(Long userId, int delta, String reasonCode, Long orderId, Long operatorId, String remark) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (delta != 0) {
            userMapper.update(null, Wrappers.<User>lambdaUpdate()
                    .eq(User::getId, userId)
                    .setSql("credit_score = GREATEST(0, credit_score + (" + delta + "))"));
        }
        User refreshed = userMapper.selectById(userId);
        int balance = refreshed == null || refreshed.getCreditScore() == null ? 0 : refreshed.getCreditScore();

        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setReasonCode(reasonCode);
        ledger.setOrderId(orderId);
        ledger.setBalanceAfter(balance);
        ledger.setOperatorId(operatorId == null ? 0L : operatorId);
        ledger.setRemark(remark);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
        return balance;
    }
}
