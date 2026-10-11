package com.weiguang.modules.incentive.dto;

import lombok.Data;

/**
 * H1 荣耀榜条目（详设 §3.6：`{nickname,avatarUrl,helpCount}`）。
 */
@Data
public class HonorItemVO {

    private String nickname;
    private String avatarUrl;
    private Long helpCount;
}