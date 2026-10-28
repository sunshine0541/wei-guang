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

    private LocalDateTime createdAt;
}
