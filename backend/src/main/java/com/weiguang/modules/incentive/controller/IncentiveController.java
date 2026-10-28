package com.weiguang.modules.incentive.controller;

import com.weiguang.common.result.IdVO;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.modules.incentive.dto.EvaluationRequest;
import com.weiguang.modules.incentive.dto.LetterRequest;
import com.weiguang.modules.incentive.dto.LedgerVO;
import com.weiguang.modules.incentive.dto.StoryCardVO;
import com.weiguang.modules.incentive.dto.ThankVO;
import com.weiguang.modules.incentive.service.IncentiveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * S1/S3/S5 与 U8/U9/U10（详设 §3.3、§3.6；S4 悄悄话已于 v1.3 下线，见 §15.9）。
 */
@RestController
public class IncentiveController {

    private final IncentiveService incentiveService;

    public IncentiveController(IncentiveService incentiveService) {
        this.incentiveService = incentiveService;
    }

    /** S1 提交评价（受助者评助人者，一单一评） */
    @PostMapping("/api/orders/{id}/evaluation")
    public Result<IdVO> evaluate(@PathVariable Long id, @RequestBody EvaluationRequest request) {
        return Result.ok(incentiveService.evaluate(AuthContext.requireUid(), id, request));
    }

    /** S2 感谢信 AI 起头（仅受助者、COMPLETED 且窗口内；不落库） */
    @PostMapping("/api/orders/{id}/thank-letter/ai-draft")
    public Result<ThankLetterDraftResult> thankLetterDraft(@PathVariable Long id) {
        return Result.ok(incentiveService.thankLetterDraft(AuthContext.requireUid(), id));
    }

    /** S3 提交感谢信（一单一封） */
    @PostMapping("/api/orders/{id}/thank-letter")
    public Result<IdVO> thankLetter(@PathVariable Long id, @RequestBody LetterRequest request) {
        return Result.ok(incentiveService.thankLetter(AuthContext.requireUid(), id, request));
    }

    /** S5 查看故事卡 */
    @GetMapping("/api/orders/{id}/story-card")
    public Result<StoryCardVO> storyCard(@PathVariable Long id) {
        return Result.ok(incentiveService.storyCard(AuthContext.requireUid(), id));
    }

    /** U8 微光值余额与明细 */
    @GetMapping("/api/user/credit")
    public Result<LedgerVO> credit(@RequestParam(required = false) Long page,
                                   @RequestParam(required = false) Long pageSize) {
        return Result.ok(incentiveService.credit(AuthContext.requireUid(), page, pageSize));
    }

    /** U9 积分余额与明细 */
    @GetMapping("/api/user/points")
    public Result<LedgerVO> points(@RequestParam(required = false) Long page,
                                   @RequestParam(required = false) Long pageSize) {
        return Result.ok(incentiveService.points(AuthContext.requireUid(), page, pageSize));
    }

    /** U10 我收到的感谢 */
    @GetMapping("/api/user/thanks")
    public Result<PageVO<ThankVO>> thanks(@RequestParam(required = false) Long page,
                                          @RequestParam(required = false) Long pageSize) {
        return Result.ok(incentiveService.thanks(AuthContext.requireUid(), page, pageSize));
    }
}