package com.weiguang.modules.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 动态卡片/详情（用户端；非 PUBLISHED 只对作者本人下发）。
 */
@Data
public class PostVO {

    private Long id;

    private Long authorId;

    private String authorNickname;

    private String authorAvatarUrl;

    /** NORMAL/STORY/OFFICIAL */
    private String category;

    private String title;

    private String content;

    private List<String> images;

    /** PENDING_REVIEW/PENDING_MANUAL/PUBLISHED/HIDDEN/REJECTED */
    private String status;

    private Integer likeCount;

    private Integer commentCount;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    private LocalDateTime createdAt;

    /** 作者可见的审核说明（非作者不下发） */
    private String reviewReason;
}
