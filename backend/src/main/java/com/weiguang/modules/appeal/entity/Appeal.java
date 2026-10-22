package com.weiguang.modules.appeal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 申诉与反馈（表 appeal；反馈复用本表，order_id 为空）。
 */
@Data
@TableName("appeal")
public class Appeal {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 申诉必填；FEEDBACK 为 NULL */
    private Long orderId;

    /** 发起人 ID */
    private Long initiatorId;

    /** CONFIRM_REJECT/POST_ORDER/FEEDBACK */
    private String type;

    /** 申诉/反馈理由 */
    private String reason;

    /** 凭证图片相对地址（可选） */
    private String evidenceUrl;

    /** PENDING/RESOLVED */
    private String status;

    /** 处理人 ID */
    private Long handlerId;

    /** TO_COMPLETED/TO_CANCELLED */
    private String result;

    /** 处理结论 */
    private String conclusion;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 处理完成时间 */
    private LocalDateTime resolvedAt;
}
