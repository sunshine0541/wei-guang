package com.weiguang.modules.admin.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.admin.dto.AdminUserDetailVO;
import com.weiguang.modules.admin.dto.AdminUserVO;
import com.weiguang.modules.admin.dto.CreditAdjustRequest;
import com.weiguang.modules.admin.service.AdminUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端用户管理（详设 §3.4）：列表/详情/封禁/解封/微光值人工处置。
 */
@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /** 用户列表（关键词/状态筛选，分页） */
    @GetMapping
    public Result<PageVO<AdminUserVO>> list(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(required = false) Long page,
                                            @RequestParam(required = false) Long pageSize) {
        return Result.ok(adminUserService.list(keyword, status, page, pageSize));
    }

    /** 用户详情（画像+微光值/积分明细+订单摘要） */
    @GetMapping("/{id}")
    public Result<AdminUserDetailVO> detail(@PathVariable Long id) {
        return Result.ok(adminUserService.detail(id));
    }

    /** 封禁 */
    @PostMapping("/{id}/ban")
    public Result<Void> ban(@PathVariable Long id) {
        adminUserService.ban(id);
        return Result.ok();
    }

    /** 解封 */
    @PostMapping("/{id}/unban")
    public Result<Void> unban(@PathVariable Long id) {
        adminUserService.unban(id);
        return Result.ok();
    }

    /** 微光值人工处置（{delta, remark}，通知 CREDIT_CHANGED） */
    @PostMapping("/{id}/credit-adjust")
    public Result<Void> creditAdjust(@PathVariable Long id, @RequestBody CreditAdjustRequest request) {
        adminUserService.creditAdjust(AuthContext.getAdminId(), id, request);
        return Result.ok();
    }
}