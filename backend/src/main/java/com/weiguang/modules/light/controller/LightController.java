package com.weiguang.modules.light.controller;

import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.light.dto.LightVO;
import com.weiguang.modules.light.service.LightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 光语 U7（详设 §3.6）。
 */
@RestController
public class LightController {

    private final LightService lightService;

    public LightController(LightService lightService) {
        this.lightService = lightService;
    }

    /** U7 我的光语状态+徽记+解释文案（含推导态"被点亮"实时计算） */
    @GetMapping("/api/user/light")
    public Result<LightVO> light() {
        return Result.ok(lightService.light(AuthContext.requireUid()));
    }
}