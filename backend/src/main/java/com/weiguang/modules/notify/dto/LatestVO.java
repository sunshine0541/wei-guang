package com.weiguang.modules.notify.dto;

import lombok.Data;

/**
 * N2 横幅最新未读（详设 §3.6）：{type,title,content,jumpType,jumpId}。
 */
@Data
public class LatestVO {

    private String type;
    private String title;
    private String content;
    private String jumpType;
    private Long jumpId;

    public LatestVO(String type, String title, String content, String jumpType, Long jumpId) {
        this.type = type;
        this.title = title;
        this.content = content;
        this.jumpType = jumpType;
        this.jumpId = jumpId;
    }
}