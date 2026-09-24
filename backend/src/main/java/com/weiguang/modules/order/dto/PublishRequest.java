package com.weiguang.modules.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * O2 发布求助入参（详设 §3.3）。
 */
@Data
public class PublishRequest {

    /** TEMPLATE / FREE / REPEAT */
    private String source;

    /** HOME_VISIT / ACCOMPANY / REMOTE */
    @NotBlank(message = "参数校验失败")
    private String orderType;

    /** 急标（勾选需二次确认，由前端执行） */
    private Integer urgentFlag;

    @NotBlank(message = "参数校验失败")
    private String title;

    private String description;

    /** 上门/陪同必填；远程禁止 */
    private Long addressId;

    private List<String> tags;

    /** ASAP / TODAY / TOMORROW / THIS_WEEK / ANY */
    private String timeExpect;

    private Long templateId;

    /** REPEAT 时必填，且必须属于本人 */
    private Long repeatFromOrderId;
}
