package com.weiguang.modules.appeal.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.appeal.dto.AppealCreateRequest;
import com.weiguang.modules.appeal.dto.AppealIdVO;
import com.weiguang.modules.appeal.dto.AppealVO;
import com.weiguang.modules.appeal.dto.FeedbackCreateRequest;
import com.weiguang.modules.appeal.service.AppealService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 申诉与反馈接口（详设 §3.6 F1–F3）。F1/F2 为封禁用户豁免路径（拦截器白名单）。
 */
@RestController
@RequestMapping("/api")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    /** F1 提交申诉 */
    @PostMapping("/appeals")
    public Result<AppealIdVO> submitAppeal(@RequestBody AppealCreateRequest request) {
        return Result.ok(new AppealIdVO(appealService.submitAppeal(AuthContext.requireUid(), request)));
    }

    /** F2 提交反馈 */
    @PostMapping("/feedbacks")
    public Result<AppealIdVO> submitFeedback(@RequestBody FeedbackCreateRequest request) {
        return Result.ok(new AppealIdVO(appealService.submitFeedback(AuthContext.requireUid(), request)));
    }

    /** F3 我的申诉/反馈列表 */
    @GetMapping("/appeals/mine")
    public Result<PageVO<AppealVO>> mine(@RequestParam(required = false) String tab,
                                         @RequestParam(required = false) Long page,
                                         @RequestParam(required = false) Long pageSize) {
        return Result.ok(appealService.mine(AuthContext.requireUid(), tab, page, pageSize));
    }
}