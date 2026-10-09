package com.weiguang.modules.order.controller;

import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.order.dto.CareTipVO;
import com.weiguang.modules.order.service.OrderService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O15 照护提示卡（详设 §3.3）：仅当前轮接单者、服务有效状态内，结束即收回（20003）。
 */
@RestController
@RequestMapping("/api/orders")
public class CareTipController {

    private final OrderService orderService;

    public CareTipController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{id}/care-tip")
    public Result<CareTipVO> careTip(@PathVariable Long id) {
        return Result.ok(orderService.careTip(AuthContext.requireUid(), id));
    }
}