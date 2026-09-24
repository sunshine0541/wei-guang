package com.weiguang.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统配置（表 sys_config，唯一事实来源）。
 */
@Data
@TableName("sys_config")
public class SysConfig {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 模块.参数名 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 描述 */
    private String description;

    /** 默认值 */
    private String defaultValue;

    /** 取值范围 */
    private String valueRange;

    /** 更新人 */
    private String updatedBy;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
