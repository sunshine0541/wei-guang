package com.weiguang.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 邻里动态（表 community_post，详设 §15.1.2）。
 */
@Data
@TableName("community_post")
public class CommunityPost {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 0 = 官方"微光小助手" */
    private Long authorId;

    /** NORMAL 邻里动态 / STORY 互助故事 / OFFICIAL 官方小贴士 */
    private String category;

    private String title;

    private String content;

    /** JSON 数组，/files 相对 URL */
    private String images;

    /** PENDING_REVIEW/PENDING_MANUAL/PUBLISHED/HIDDEN/REJECTED/DELETED */
    private String status;

    /** AI 降级放行标记 */
    private Integer reviewDegraded;

    /** 本地命中词/AI 结论摘要 */
    private String reviewReason;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;
}
