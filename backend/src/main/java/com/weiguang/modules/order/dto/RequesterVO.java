package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * 列表/详情中的发布者信息（仅昵称与头像，不含年龄/地址/微光值）。
 */
@Data
public class RequesterVO {

    private String nickname;
    private String avatarUrl;
}
