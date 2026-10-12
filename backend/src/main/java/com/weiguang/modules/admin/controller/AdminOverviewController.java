package com.weiguang.modules.admin.controller;

import com.weiguang.common.result.Result;
import com.weiguang.modules.admin.dto.AdminOverviewVO;
import com.weiguang.modules.admin.service.AdminOverviewService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端数据概览（详设 §3.4 GET /admin/overview）。
 */
@RestController
@RequestMapping("/admin/overview")
public class AdminOverviewController {

    private final AdminOverviewService adminOverviewService;

    public AdminOverviewController(AdminOverviewService adminOverviewService) {
        this.adminOverviewService = adminOverviewService;
    }

    /** 4 个数字：用户数、求助总数、已完成数、类型分布 */
    @GetMapping
    public Result<AdminOverviewVO> overview() {
        return Result.ok(adminOverviewService.overview());
    }
}