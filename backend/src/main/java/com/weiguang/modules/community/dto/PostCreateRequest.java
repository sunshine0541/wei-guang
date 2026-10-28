package com.weiguang.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * CM3 发布动态请求（OFFICIAL 仅管理端）。
 */
@Data
public class PostCreateRequest {

    /** NORMAL/STORY */
    @NotBlank
    private String category;

    private String title;

    @NotBlank
    private String content;

    /** /files 相对 URL 列表，≤community.post_image_max */
    private List<String> images;
}
