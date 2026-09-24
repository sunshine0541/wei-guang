package com.weiguang.common.result;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 仅返回主键的响应体（详设 §3.6：B2/F1/F2 的 data 为 {id}）。
 */
@Data
@AllArgsConstructor
public class IdVO {

    private Long id;

    public static IdVO of(Long id) {
        return new IdVO(id);
    }
}
