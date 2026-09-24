package com.weiguang.modules.system.vo;

import com.weiguang.modules.system.entity.SysConfig;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置列表项（详设 §3.4 GET /admin/configs：key/当前值/默认值/范围/说明）。
 */
@Data
public class ConfigVO {

    private String key;
    private String value;
    private String defaultValue;
    private String valueRange;
    private String description;
    private String updatedBy;
    private LocalDateTime updatedAt;

    public static ConfigVO from(SysConfig config) {
        ConfigVO vo = new ConfigVO();
        vo.setKey(config.getConfigKey());
        vo.setValue(config.getConfigValue());
        vo.setDefaultValue(config.getDefaultValue());
        vo.setValueRange(config.getValueRange());
        vo.setDescription(config.getDescription());
        vo.setUpdatedBy(config.getUpdatedBy());
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }
}
