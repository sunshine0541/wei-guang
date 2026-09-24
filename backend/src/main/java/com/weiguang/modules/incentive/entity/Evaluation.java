package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评价（表 evaluation，一单一评）。
 */
@Data
@TableName("evaluation")
public class Evaluation {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 求助者 ID */
    private Long requesterId;

    /** 助人者 ID */
    private Long helperId;

    /** 1-5，必填 */
    private Integer stars;

    /** 可选一句建议 */
    private String advice;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
