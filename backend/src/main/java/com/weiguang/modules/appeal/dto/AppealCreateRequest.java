package com.weiguang.modules.appeal.dto;

import lombok.Data;

/**
 * F1 POST /api/appeals 入参（详设 §3.6）：{orderId, type: POST_ORDER, reason}。
 * CONFIRM_REJECT 由 O12 拒绝路径自动生成，不经本接口。
 */
@Data
public class AppealCreateRequest {

    /** 关联求助单（必填，归属=订单双方之一） */
    private Long orderId;

    /** 固定 POST_ORDER（其余取值拒绝） */
    private String type;

    /** 理由（必填 ≤200） */
    private String reason;

    /** 凭证图片相对地址（可选，先走 /api/upload/image 上传） */
    private String evidenceUrl;
}