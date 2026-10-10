package com.weiguang.modules.appeal.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.appeal.dto.AdminAppealDetailVO;
import com.weiguang.modules.appeal.dto.AdminAppealVO;
import com.weiguang.modules.appeal.dto.ArbitrateRequest;
import com.weiguang.modules.appeal.dto.FeedbackReplyRequest;
import com.weiguang.modules.appeal.service.AppealService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端申诉/反馈接口（详设 §3.4）。
 */
@RestController
@RequestMapping("/admin")
public class AdminAppealController {

    private final AppealService appealService;

    public AdminAppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /** 申诉/反馈列表（type/status 筛选） */
    @GetMapping("/appeals")
    public Result<PageVO<AdminAppealVO>> list(@RequestParam(required = false) String type,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) Long page,
                                              @RequestParam(required = false) Long pageSize) {
        return Result.ok(appealService.adminList(type, status, page, pageSize));
    }

    /** 详情（申诉含订单/时间线/聊天记录；反馈仅理由与结论） */
    @GetMapping("/appeals/{id}")
    public Result<AdminAppealDetailVO> detail(@PathVariable Long id) {
        return Result.ok(appealService.adminDetail(id));
    }

    /** 仲裁（仅申诉类型，FEEDBACK → 40002） */
    @PostMapping("/appeals/{id}/arbitrate")
    public Result<Void> arbitrate(@PathVariable Long id, @RequestBody ArbitrateRequest request) {
        appealService.arbitrate(AuthContext.getAdminId(), id, request);
        return Result.ok();
    }

    /** 反馈处理（结论 → 通知 FEEDBACK_RESULT） */
    @PostMapping("/feedbacks/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody FeedbackReplyRequest request) {
        appealService.replyFeedback(AuthContext.getAdminId(), id, request.getConclusion());
        return Result.ok();
    }
}