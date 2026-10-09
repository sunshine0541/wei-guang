package com.weiguang.modules.incentive.dto;

import lombok.Data;

import java.util.List;

/**
 * U8/U9 出参：余额 + 明细分页（详设 §3.6 `{balance, records[],total,current,size}`）。
 */
@Data
public class LedgerVO {

    private Integer balance;
    private List<LedgerRecordVO> records;
    private Long total;
    private Long current;
    private Long size;
}