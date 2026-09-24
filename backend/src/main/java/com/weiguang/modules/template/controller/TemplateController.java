package com.weiguang.modules.template.controller;

import com.weiguang.common.result.Result;
import com.weiguang.modules.template.dto.TemplateVO;
import com.weiguang.modules.template.service.HelpTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * T1 求助模板列表（详设 §3.2）。
 */
@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final HelpTemplateService helpTemplateService;

    public TemplateController(HelpTemplateService helpTemplateService) {
        this.helpTemplateService = helpTemplateService;
    }

    @GetMapping
    public Result<List<TemplateVO>> list() {
        return Result.ok(helpTemplateService.list());
    }
}
