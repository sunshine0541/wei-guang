package com.weiguang.modules.appeal.dto;

import lombok.Data;

import java.util.List;

/**
 * POST /admin/appeals/{id}/arbitrate 入参（详设 §3.4）：
 * {result:TO_COMPLETED|TO_CANCELLED, conclusion, penalties?[{userId,reasonCode:APPEAL_LOSS}], revertCreditIds?[]}。
 */
@Data
public class ArbitrateRequest {

    /** TO_COMPLETED 落回已完成 / TO_CANCELLED 落回已取消 */
    private String result;

    /** 仲裁结论（必填 ≤255，写入时间线） */
    private String conclusion;

    /** 败诉方处置（按方选择，可双方分别处置） */
    private List<Penalty> penalties;

    /** TO_CANCELLED 追回时指定要反向的微光值明细 id；为空则自动反向该单全部 COMPLETE 明细 */
    private List<Long> revertCreditIds;

    @Data
    public static class Penalty {

        private Long userId;

        /** 固定 APPEAL_LOSS */
        private String reasonCode;
    }
}