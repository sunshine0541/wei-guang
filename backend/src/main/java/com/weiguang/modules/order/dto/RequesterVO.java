package com.weiguang.modules.order.dto;

import lombok.Data;

/**
 * 列表/详情中的发布者信息（仅 ID/昵称/头像，不含年龄/地址/微光值）。
 * id 供前端点击昵称头像进入邻居公开页（U11，见《实现偏差清单》D12）。
 */
@Data
public class RequesterVO {

    private Long id;
    private String nickname;
    private String avatarUrl;
}
