package com.weiguang.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * O16 修改待响应求助入参（仅发布者、仅 PENDING；类型与地址不可改，D53）。
 */
@Data
public class ModifyOrderRequest {

    @NotBlank(message = "参数校验失败")
    private String title;

    private String description;

    private List<String> tags;

    /** 自定义标签（≤10 字，仅展示、不入推荐）；传空串表示清空，null 表示保持不变 */
    private String extraTag;

    /** ASAP / TODAY / TOMORROW / THIS_WEEK / ANY；null 表示保持不变 */
    private String timeExpect;

    /** null 表示保持不变 */
    private Integer urgentFlag;
}
