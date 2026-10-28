package com.weiguang.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理端动态/评论审核：APPROVE / REJECT。
 */
@Data
public class AdminPostReviewRequest {

    @NotBlank
    private String result;

    private String note;
}
