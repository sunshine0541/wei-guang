package com.weiguang.modules.security.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 敏感词与防诈要素词库（表 sensitive_word）。
 */
@Data
@TableName("sensitive_word")
public class SensitiveWord {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 敏感词/防诈要素 */
    private String word;

    /** SENSITIVE/FRAUD */
    private String category;

    /** BLOCK/HIGHLIGHT/REVIEW */
    private String action;

    /** 是否启用 */
    private Integer enabled;
}
