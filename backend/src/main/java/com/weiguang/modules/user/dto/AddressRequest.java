package com.weiguang.modules.user.dto;

import com.weiguang.common.constants.ProfileConstants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

    /** POI 纬度（高德选点后带入；手动输入可为空） */
    @DecimalMin(value = "-90.0", message = "参数校验失败")
    @DecimalMax(value = "90.0", message = "参数校验失败")
    private Double latitude;

    /** POI 经度（高德选点后带入；手动输入可为空） */
    @DecimalMin(value = "-180.0", message = "参数校验失败")
    @DecimalMax(value = "180.0", message = "参数校验失败")
    private Double longitude;

    private Integer isDefault;
}
