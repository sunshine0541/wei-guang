package com.weiguang.modules.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.result.Result;
import com.weiguang.modules.system.entity.SysConfig;
import com.weiguang.modules.system.mapper.SysConfigMapper;
import com.weiguang.modules.system.vo.ConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置查询（详设 §3.4 GET /admin/configs）。T02 仅只读，编辑接口在 T17 补齐。
 */
@RestController
@RequestMapping("/admin/configs")
public class ConfigController {

    private final SysConfigMapper sysConfigMapper;

    public ConfigController(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    @GetMapping
    public Result<List<ConfigVO>> list() {
        List<SysConfig> configs = sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getId));
        return Result.ok(configs.stream().map(ConfigVO::from).toList());
    }
}
