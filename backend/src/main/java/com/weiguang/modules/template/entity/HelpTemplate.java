package com.weiguang.modules.template.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 求助模板（表 help_template）。
 */
@Data
@TableName("help_template")
public class HelpTemplate {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 模板名 */
    private String name;

    /** emoji 图标 */
    private String icon;

    /** 覆盖的小事描述 */
    private String coverDesc;

    /** HOME_VISIT/ACCOMPANY/REMOTE */
    private String defaultType;

    /** 预填规范标题 */
    private String title;

    /** 展示排序 */
    private Integer sort;

    /** 是否启用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;
}
