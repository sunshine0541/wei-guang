package com.weiguang.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * U2 PUT /api/user/profile 出参：更新后的档案 + 按确认项 10 重算的帮助方向草稿（待用户确认）。
 */
@Data
@AllArgsConstructor
public class ProfileUpdateVO {

    private UserProfileVO profile;

    private List<String> helpDirectionsDraft;
}
