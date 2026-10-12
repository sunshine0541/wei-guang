package com.weiguang.modules.system.controller;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.system.dto.ConfigUpdateRequest;
import com.weiguang.modules.system.entity.SysConfig;
import com.weiguang.modules.system.mapper.SysConfigMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.system.vo.ConfigVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 配置管理（详设 §3.4）：列表 + 编辑（范围校验，保存即刷新缓存生效）。
 */
@RestController
@RequestMapping("/admin/configs")
public class ConfigController {

    private final SysConfigMapper sysConfigMapper;
    private final ConfigService configService;

    public ConfigController(SysConfigMapper sysConfigMapper, ConfigService configService) {
        this.sysConfigMapper = sysConfigMapper;
        this.configService = configService;
    }

    /** 配置列表（key/当前值/默认值/范围/说明） */
    @GetMapping
    public Result<List<ConfigVO>> list() {
        List<SysConfig> configs = sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery().orderByAsc(SysConfig::getId));
        return Result.ok(configs.stream().map(ConfigVO::from).toList());
    }

    /** 修改配置（按 value_range 校验；保存即刷新缓存，改即生效） */
    @PutMapping("/{key}")
    public Result<Void> update(@PathVariable String key, @RequestBody ConfigUpdateRequest request) {
        configService.update(AuthContext.getAdminId(), key, request.getValue());
        return Result.ok();
    }
}