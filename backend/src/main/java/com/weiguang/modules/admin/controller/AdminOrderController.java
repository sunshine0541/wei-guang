package com.weiguang.modules.admin.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.admin.dto.AdminOrderDetailVO;
import com.weiguang.modules.admin.dto.AdminOrderVO;
import com.weiguang.modules.admin.dto.CancelArbitrateRequest;
import com.weiguang.modules.admin.service.AdminOrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单接口（详设 §3.4）：列表/详情/协商取消裁决。
 */
@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /** 订单列表（状态/类型/关键词筛选，分页） */
    @GetMapping
    public Result<PageVO<AdminOrderVO>> list(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String orderType,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long page,
                                             @RequestParam(required = false) Long pageSize) {
        return Result.ok(adminOrderService.list(status, orderType, keyword, page, pageSize));
    }

    /** 订单详情（时间线/双方/聊天记录门控/裁决可用标记） */
    @GetMapping("/{id}")
    public Result<AdminOrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(adminOrderService.detail(id));
    }

    /** 协商取消裁决（CANCEL/KEEP + 结论） */
    @PostMapping("/{id}/cancel-arbitrate")
    public Result<Void> cancelArbitrate(@PathVariable Long id, @RequestBody CancelArbitrateRequest request) {
        adminOrderService.cancelArbitrate(AuthContext.getAdminId(), id, request);
        return Result.ok();
    }
}