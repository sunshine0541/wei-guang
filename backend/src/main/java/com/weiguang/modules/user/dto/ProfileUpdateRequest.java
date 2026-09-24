package com.weiguang.modules.user.dto;

import com.weiguang.common.constants.ProfileConstants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * U2 PUT /api/user/profile 入参（详设 §3.3：字段级校验，长度见确认项 8）。
 * 全部字段选填，只更新传入的字段。
 */
@Data
public class ProfileUpdateRequest {

    @Size(max = ProfileConstants.NICKNAME_MAX_LENGTH, message = "内容超长")
    private String nickname;

    @Size(max = 255, message = "内容超长")
    private String avatarUrl;

    @Min(value = ProfileConstants.AGE_MIN, message = "参数校验失败")
    @Max(value = ProfileConstants.AGE_MAX, message = "参数校验失败")
    private Integer age;

    @Size(max = ProfileConstants.SIGNATURE_MAX_LENGTH, message = "内容超长")
    private String signature;

    private List<String> hobbies;

    @Size(max = ProfileConstants.CARE_TIP_MAX_LENGTH, message = "内容超长")
    private String careTip;

    private List<String> helpDirections;

    @Size(max = ProfileConstants.NICKNAME_MAX_LENGTH, message = "内容超长")
    private String emergencyContactName;

    @Size(max = ProfileConstants.NICKNAME_MAX_LENGTH, message = "内容超长")
    private String emergencyContactPhone;

    @Size(max = ProfileConstants.NICKNAME_MAX_LENGTH, message = "内容超长")
    private String phone;
}
