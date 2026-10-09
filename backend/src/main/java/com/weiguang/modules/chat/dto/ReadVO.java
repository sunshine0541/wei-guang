package com.weiguang.modules.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * C4 POST /api/sessions/{id}/read 出参（详设 §3.2：{cleared: n}）。
 */
@Data
@AllArgsConstructor
public class ReadVO {

    /** 本次清零的未读数 */
    private Integer cleared;
}