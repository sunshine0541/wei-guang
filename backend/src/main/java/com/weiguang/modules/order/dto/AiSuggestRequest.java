package com.weiguang.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * O1 求助理解入参（详设 §3.3）。
 */
@Data
public class AiSuggestRequest {

    @NotBlank(message = "参数校验失败")
    private String text;
}
