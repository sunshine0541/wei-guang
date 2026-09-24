package com.weiguang.modules.incentive.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 光之故事卡（表 story_card）。
 */
@Data
@TableName("story_card")
public class StoryCard {

    /** 主键 */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID */
    private Long orderId;

    /** 求助者姓名 */
    private String requesterName;

    /** 助人者姓名 */
    private String helperName;

    /** 事情（取求助标题） */
    private String thing;

    /** 一句话小结（本地模板拼接） */
    private String summary;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
