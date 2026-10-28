package com.weiguang.modules.community.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 管理端发布官方动态（category 固定 OFFICIAL，直接 PUBLISHED）。
 */
@Data
public class AdminOfficialPostRequest {

    private String title;

    @NotBlank
    @Size(max = 1000)
    private String content;

    private List<String> images;
}
