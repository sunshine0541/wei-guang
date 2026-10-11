package com.weiguang.modules.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import org.springframework.stereotype.Service;

/**
 * 防诈上报（详设 §2.20；管理端列表见 §3.4 GET /admin/reports）。
 */
@Service
public class ReportService {

    private final ReportMapper reportMapper;

    public ReportService(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
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