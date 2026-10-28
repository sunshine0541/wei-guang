package com.weiguang.modules.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理端动态视图（含审核标记）。
 */
@Data
public class AdminPostVO {

    private Long id;

    private Long authorId;

    private String authorNickname;

    private String category;

    private String title;

    private String content;

    private List<String> images;

    private String status;

    private Integer reviewDegraded;

    private String reviewReason;

    private Integer likeCount;

    private Integer commentCount;

    private LocalDateTime createdAt;
}
