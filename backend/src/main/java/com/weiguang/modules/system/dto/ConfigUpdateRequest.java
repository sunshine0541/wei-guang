package com.weiguang.modules.system.dto;

import lombok.Data;

/**
 * PUT /admin/configs/{key} 入参（详设 §3.4）：{value}，按 value_range 校验，保存即刷新缓存。
 */
@Data
public class ConfigUpdateRequest {

    private String value;
}