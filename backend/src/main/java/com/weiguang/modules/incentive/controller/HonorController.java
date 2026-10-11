package com.weiguang.modules.incentive.controller;

import com.weiguang.common.result.Result;
import com.weiguang.modules.incentive.dto.HomeSummaryVO;
import com.weiguang.modules.incentive.service.HonorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * H1 首页聚合接口（详设 §3.6）：荣耀榜 TOP10 + 善意回流计数。
 */
@RestController
public class HonorController {

    private final HonorService honorService;

    public HonorController(HonorService honorService) {
        this.honorService = honorService;
    }

    /** H1 首页聚合 */
    @GetMapping("/api/home/summary")
    public Result<HomeSummaryVO> summary() {
        return Result.ok(honorService.summary());
    }
}