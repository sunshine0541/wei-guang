package com.weiguang.modules.order.controller;

import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.order.dto.AcceptVO;
import com.weiguang.modules.order.dto.AiSuggestRequest;
import com.weiguang.modules.order.dto.CancelConfirmRequest;
import com.weiguang.modules.order.dto.CancelRequest;
import com.weiguang.modules.order.dto.ConfirmRequest;
import com.weiguang.modules.order.dto.MyOrderVO;
import com.weiguang.modules.order.dto.OrderCardVO;
import com.weiguang.modules.order.dto.OrderDetailVO;
import com.weiguang.modules.order.dto.PublishRequest;
import com.weiguang.modules.order.dto.PublishVO;
import com.weiguang.modules.order.dto.ReleaseRequest;
import com.weiguang.modules.order.dto.TimelineVO;
import com.weiguang.modules.order.service.CancelService;
import com.weiguang.modules.order.service.OrderService;
import com.weiguang.modules.order.service.ReleaseService;
import com.weiguang.modules.order.service.SettleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 求助单接口（详设 §3.2 O1–O14）。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final SettleService settleService;
    private final ReleaseService releaseService;
    private final CancelService cancelService;

    public OrderController(OrderService orderService,
                           SettleService settleService,
                           ReleaseService releaseService,
                           CancelService cancelService) {
        this.orderService = orderService;
        this.settleService = settleService;
        this.releaseService = releaseService;
        this.cancelService = cancelService;
    }

    /** O1 能力一：自由描述 → 类型/标签/紧急度/标题建议（不落库） */
    @PostMapping("/ai-suggest")
    public Result<UnderstandHelpResult> aiSuggest(@Valid @RequestBody AiSuggestRequest request) {
        return Result.ok(orderService.aiSuggest(request.getText()));
    }

    /** O2 发布求助（限流 3 次/分） */
    @PostMapping
    @RateLimit(prefix = "rate:publish:", limitKey = "rate.publish_per_minute")
    public Result<PublishVO> publish(@Valid @RequestBody PublishRequest request) {
        return Result.ok(orderService.publish(AuthContext.requireUid(), request));
    }

    /** O3 求助列表（tab=all|recommend） */
    @GetMapping
    public Result<PageVO<OrderCardVO>> list(@RequestParam(required = false) String tab,
                                            @RequestParam(required = false) String orderType,
                                            @RequestParam(required = false) Boolean urgentOnly,
                                            @RequestParam(required = false) Long page,
                                            @RequestParam(required = false) Long pageSize) {
        return Result.ok(orderService.list(AuthContext.requireUid(), tab, orderType, urgentOnly, page, pageSize));
    }

    /** O5 我的求助列表 */
    @GetMapping("/mine")
    public Result<PageVO<MyOrderVO>> mine(@RequestParam(required = false) String tab,
                                          @RequestParam(required = false) Long page,
                                          @RequestParam(required = false) Long pageSize) {
        return Result.ok(orderService.mine(AuthContext.requireUid(), tab, page, pageSize));
    }

    /** O4 求助详情（写入浏览去重） */
    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(AuthContext.requireUid(), id));
    }

    /** O6 接单（"我愿意帮助"） */
    @PostMapping("/{id}/accept")
    public Result<AcceptVO> accept(@PathVariable Long id) {
        return Result.ok(orderService.accept(AuthContext.requireUid(), id));
    }

    /** O7 确认开始（仅当前轮接单者） */
    @PostMapping("/{id}/start")
    public Result<Void> start(@PathVariable Long id) {
        orderService.start(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** O8 释放（放弃/实在帮不上/服务中退出） */
    @PostMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestBody ReleaseRequest request) {
        releaseService.release(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** O9 取消（mode=CANCEL 单方取消 / NEGOTIATE 发起协商，见 D13） */
    @PostMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody CancelRequest request) {
        cancelService.cancel(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** O10 协商取消应答（仅另一方） */
    @PostMapping("/{id}/cancel-confirm")
    public Result<Void> cancelConfirm(@PathVariable Long id, @RequestBody CancelConfirmRequest request) {
        cancelService.cancelConfirm(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** O14 订单时间线（归属=订单双方） */
    @GetMapping("/{id}/timeline")
    public Result<List<TimelineVO>> timeline(@PathVariable Long id) {
        return Result.ok(orderService.timeline(AuthContext.requireUid(), id));
    }

    /** O11 标记完成（发起双确认） */
    @PostMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id) {
        settleService.complete(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** O12 确认/拒绝完成（逐字段见详设 §3.3） */
    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @RequestBody ConfirmRequest request) {
        settleService.confirm(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** O13 一键重发（仅 EXPIRED 态） */
    @PostMapping("/{id}/republish")
    public Result<PublishVO> republish(@PathVariable Long id) {
        return Result.ok(orderService.republish(AuthContext.requireUid(), id));
    }
}
