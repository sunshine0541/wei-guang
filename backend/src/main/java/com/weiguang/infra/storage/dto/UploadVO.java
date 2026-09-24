package com.weiguang.infra.storage.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * P1 图片上传出参（详设 §3.3 P1：返回 {url}）。
 */
@Data
@AllArgsConstructor
public class UploadVO {

    private String url;
}
