package com.weiguang.modules.community.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 社区邻里动态项（只读，展示最近完成的互助，不含地址、门牌与求助描述）。
 */
@Data
public class CommunityFeedVO {

    private Long orderId;
    private String title;
    private String orderType;
    private List<String> tags;
    private Long helperId;
    private String helperNickname;
    private String helperAvatarUrl;
    private String requesterNickname;
    private LocalDateTime completedAt;
}
