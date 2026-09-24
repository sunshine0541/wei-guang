package com.weiguang.modules.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 浏览去重记录（表 order_view，只增）。
 */
@Data
@TableName("order_view")
public class OrderView {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 发布者本人不写入 */
    private Long userId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
