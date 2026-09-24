package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 悄悄话（表 whisper，只增不改不删）。
 */
@Data
@TableName("whisper")
public class Whisper {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 发送者 ID */
    private Long senderId;

    /** 接收者 ID */
    private Long receiverId;

    /** 悄悄话内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
