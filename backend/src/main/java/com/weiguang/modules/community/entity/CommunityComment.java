package com.weiguang.modules.community.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 动态评论（表 community_comment，一级评论，详设 §15.1.2）。
 */
@Data
@TableName("community_comment")
public class CommunityComment {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long postId;

    private Long authorId;

    private String content;

    /** PENDING_REVIEW/PENDING_MANUAL/PUBLISHED/DELETED */
    private String status;

    private LocalDateTime createdAt;
}
