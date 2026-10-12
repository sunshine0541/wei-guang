package com.weiguang.modules.report.dto;

import lombok.Data;

/**
 * POST /admin/reports/{id}/handle 入参（详设 §3.4）：
 * {result:CONFIRMED|REJECTED, note, action?:NONE|CREDIT_MINUS|BAN, creditDelta?:-10|-20}。
 */
@Data
public class ReportHandleRequest {

    /** CONFIRMED=确认违规 / REJECTED=判定误报 */
    private String result;
    /** 处理备注（必填） */
    private String note;
    /** 联动动作：NONE/CREDIT_MINUS/BAN（默认 NONE） */
    private String action;
    /** 微光值扣减幅度（action=CREDIT_MINUS 时必填：-10 或 -20） */
    private Integer creditDelta;
}