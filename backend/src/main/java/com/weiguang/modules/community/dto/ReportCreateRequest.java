package com.weiguang.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * CM12 举报动态/评论。
 */
@Data
public class ReportCreateRequest {

    /** POST/COMMENT */
    @NotBlank
    private String targetType;

    @NotNull
    private Long targetId;

    @NotBlank
    private String reason;
}
