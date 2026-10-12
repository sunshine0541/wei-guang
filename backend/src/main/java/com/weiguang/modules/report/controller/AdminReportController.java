package com.weiguang.modules.report.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.dto.ReportHandleRequest;
import com.weiguang.modules.report.service.ReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端防诈上报（详设 §3.4）：列表 + 处置。
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

    /** POST /admin/reports/{id}/handle 处置（结论+备注+联动扣减/封禁） */
    @PostMapping("/admin/reports/{id}/handle")
    public Result<Void> handle(@PathVariable Long id, @RequestBody ReportHandleRequest request) {
        reportService.handle(AuthContext.getAdminId(), id, request);
        return Result.ok();
    }
}