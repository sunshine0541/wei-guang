package com.weiguang.modules.community.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * CM3 发布动态响应。
 */
@Data
@AllArgsConstructor
public class PostCreateVO {

    private Long id;

    /** PENDING_REVIEW/PENDING_MANUAL/PUBLISHED */
    private String status;
}
