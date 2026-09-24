package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 感谢信（表 thank_letter，一单最多一封）。
 */
@Data
@TableName("thank_letter")
public class ThankLetter {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 发送者 ID */
    private Long senderId;

    /** 助人者 ID */
    private Long helperId;

    /** 感谢信内容 */
    private String content;

    /** 是否 AI 辅助 */
    private Integer aiAssisted;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
