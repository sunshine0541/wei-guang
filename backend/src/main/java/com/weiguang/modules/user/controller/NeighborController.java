package com.weiguang.modules.user.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.modules.incentive.dto.NeighborEvaluationVO;
import com.weiguang.modules.incentive.dto.NeighborProfileVO;
import com.weiguang.modules.incentive.service.IncentiveService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * U11/U12 邻居公开页（详设 §3.6：不含年龄/地址/小习惯/紧急联系人/微光值）。
 */
@RestController
@RequestMapping("/api/users")
public class NeighborController {

    private final IncentiveService incentiveService;

    public NeighborController(IncentiveService incentiveService) {
        this.incentiveService = incentiveService;
    }

    /** U11 邻居公开页（昵称/头像/签名/评价均值与计数/感谢墙） */
    @GetMapping("/{id}/profile")
    public Result<NeighborProfileVO> profile(@PathVariable Long id) {
        return Result.ok(incentiveService.neighborProfile(id));
    }

    /** U12 邻居收到的评价列表 */
    @GetMapping("/{id}/evaluations")
    public Result<PageVO<NeighborEvaluationVO>> evaluations(@PathVariable Long id,
                                                            @RequestParam(required = false) Long page,
                                                            @RequestParam(required = false) Long pageSize) {
        return Result.ok(incentiveService.neighborEvaluations(id, page, pageSize));
    }
}