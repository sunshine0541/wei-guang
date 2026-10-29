package com.weiguang.modules.assistant.dto;

import lombok.Data;

import java.util.List;

/**
 * 助手整理出的发布草稿（只用于前端预览与确认，不直接落库）。
 */
@Data
public class PublishDraftVO {

    private String orderType;
    private String title;
    private String description;
    private List<String> tags;
    private String extraTag;
    private String timeExpect;
    private Integer urgentFlag;
    private Long addressId;
    private String addressLabel;
}
