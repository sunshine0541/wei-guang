package com.weiguang.modules.user.dto;

import com.weiguang.common.constants.ProfileConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * B2/B3 地址簿入参（详设 §3.6）。
 */
@Data
public class AddressRequest {

    @NotBlank(message = "参数校验失败")
    @Size(max = ProfileConstants.ADDRESS_LABEL_MAX_LENGTH, message = "内容超长")
    private String label;

    @NotBlank(message = "参数校验失败")
    @Size(max = ProfileConstants.ADDRESS_TEXT_MAX_LENGTH, message = "内容超长")
    private String areaText;

    @Size(max = ProfileConstants.ADDRESS_TEXT_MAX_LENGTH, message = "内容超长")
    private String detailText;

    private Integer isDefault;
}
