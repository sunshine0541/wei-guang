package com.weiguang.modules.report.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端防诈上报（详设 §3.4；处置接口 POST /admin/reports/{id}/handle 属 T17 管理后台范围）。
 */
@RestController
public class AdminReportController {

    private final ReportService reportService;

    public AdminReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /** GET /admin/reports 上报列表（状态筛选） */
    @GetMapping("/admin/reports")
    public Result<PageVO<AdminReportVO>> list(@RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long page,
                                              @RequestParam(required = false) Long pageSize) {
        return Result.ok(reportService.adminList(status, page, pageSize));
    }
}