package com.weiguang.modules.notify.dto;

import lombok.Data;

/**
 * N4 全部已读（详设 §3.6）：{cleared: n}。
 */
@Data
public class ReadAllVO {

    private int cleared;

    public ReadAllVO(int cleared) {
        this.cleared = cleared;
    }
}