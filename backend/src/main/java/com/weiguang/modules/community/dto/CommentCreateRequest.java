package com.weiguang.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * CM10 发表评论。
 */
@Data
public class CommentCreateRequest {

    @NotBlank
    private String content;
}
