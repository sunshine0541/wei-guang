package com.weiguang.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 释放重派记录（表 release_record，只增不改不删）。
 */
@Data
@TableName("release_record")
public class ReleaseRecord {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 释放前轮次 */
    private Integer round;

    /** ACCEPTED/IN_SERVICE */
    private String fromStatus;

    /** START_TIMEOUT/HELPER_QUIT/HELPLESS/HELPER_LOST */
    private String reasonType;

    /** 操作者，0=系统定时任务 */
    private Long operatorId;

    /** 释放原因文本 */
    private String reasonText;

    /** NONE/MINUS5/MINUS15 */
    private String penalty;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
