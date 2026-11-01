package com.weiguang.modules.community.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * CM9 评论列表项。
 */
@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long authorId;

    private String authorNickname;

    private String authorAvatarUrl;

    private String content;

    /** 评论审核状态（管理端判断是否可审核） */
    private String status;

    private LocalDateTime createdAt;
}
