package com.weiguang.modules.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * POST /admin/orders/{id}/risk-review 入参（4C 风险核实）：
 * result=NORMAL（核实正常）/ DEDUCT（确认有问题，微光值已另行调整）。
 */
@Data
public class RiskReviewRequest {

    @NotBlank(message = "参数校验失败")
    private String result;

    @Size(max = 200, message = "内容超长")
    private String note;
}
