package com.weiguang.modules.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.dto.ReportHandleRequest;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 防诈上报（详设 §2.20；管理端列表/处置见 §3.4）。
 */
@Slf4j
@Service
public class ReportService {

    private static final int NOTE_MAX = 200;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final NotifyService notifyService;

    public ReportService(ReportMapper reportMapper,
                         UserMapper userMapper,
                         CreditLedgerMapper creditLedgerMapper,
                         NotifyService notifyService) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.notifyService = notifyService;
    }

    /** 管理端上报列表（状态筛选可选，按时间倒序） */
    public PageVO<AdminReportVO> adminList(String status, Long page, Long pageSize) {
        Page<Report> result = reportMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<Report>lambdaQuery()
                        .eq(status != null && !status.isBlank(), Report::getStatus, status)
                        .orderByDesc(Report::getId));
        return PageVO.of(result.convert(this::toVO));
    }

    /**
     * POST /admin/reports/{id}/handle 处置（详设 §3.4）：
     * 确认违规/判定误报 + 备注 + 联动（微光值扣减或封禁）+ 通知（ADMIN_ACTION）；
     * 条件更新防重复处置（影响 0 行 → 40003）。
     */
    @Transactional
    public void handle(Long adminId, Long id, ReportHandleRequest request) {
        String result = request == null ? null : request.getResult();
        String note = request == null ? null : request.getNote();
        String action = request == null || request.getAction() == null || request.getAction().isBlank()
                ? "NONE" : request.getAction();
        Integer creditDelta = request == null ? null : request.getCreditDelta();
        if (!"CONFIRMED".equals(result) && !"REJECTED".equals(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (note == null || note.isBlank() || note.length() > NOTE_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (!"NONE".equals(action) && !"CREDIT_MINUS".equals(action) && !"BAN".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if ("REJECTED".equals(result) && !"NONE".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if ("CREDIT_MINUS".equals(action)
                && (creditDelta == null || (creditDelta != -10 && creditDelta != -20))) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int rows = reportMapper.update(null, Wrappers.<Report>lambdaUpdate()
                .eq(Report::getId, id)
                .eq(Report::getStatus, "PENDING")
                .set(Report::getStatus, result)
                .set(Report::getHandlerId, adminId)
                .set(Report::getHandleNote, note)
                .set(Report::getAction, action)
                .set(Report::getHandledAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }

        Long target = report.getTargetUserId();
        if ("CONFIRMED".equals(result) && target != null && !"NONE".equals(action)) {
            if ("CREDIT_MINUS".equals(action)) {
                applyCreditDelta(target, creditDelta, adminId, note);
            } else {
                userMapper.update(null, Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, target)
                        .ne(User::getStatus, "CANCELLED")
                        .set(User::getStatus, "BANNED"));
            }
            notifyService.adminAction(target);
        }
        log.info("上报处置：report={} result={} action={} admin={}", id, result, action, adminId);
    }

    /** 联动扣减：微光值（下限 0 收敛）+ 明细（ADMIN_ADJUST） */
    private void applyCreditDelta(Long userId, int delta, Long adminId, String note) {
        User user = userMapper.selectById(userId);
        int current = user == null || user.getCreditScore() == null ? 0 : user.getCreditScore();
        int balance = Math.max(0, current + delta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getCreditScore, balance));
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(userId);
        ledger.setDelta(delta);
        ledger.setReasonCode("ADMIN_ADJUST");
        ledger.setBalanceAfter(balance);
        ledger.setOperatorId(adminId);
        ledger.setRemark("上报处置：" + note);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
    }

    private AdminReportVO toVO(Report report) {
        AdminReportVO vo = new AdminReportVO();
        vo.setId(report.getId());
        vo.setOrderId(report.getOrderId());
        vo.setMessageId(report.getMessageId());
        vo.setTargetUserId(report.getTargetUserId());
        vo.setType(report.getType());
        vo.setEvidence(report.getEvidence());
        vo.setStatus(report.getStatus());
        vo.setAction(report.getAction());
        vo.setHandleNote(report.getHandleNote());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());
        return vo;
    }
}