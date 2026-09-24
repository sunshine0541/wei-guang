package com.weiguang.common.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;

/**
 * 统一分页返回体（详设 §3.1：{records,total,current,size}）。
 */
@Data
public class PageVO<T> {

    private List<T> records;
    private long total;
    private long current;
    private long size;

    public static <T> PageVO<T> of(IPage<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setRecords(page.getRecords());
        vo.setTotal(page.getTotal());
        vo.setCurrent(page.getCurrent());
        vo.setSize(page.getSize());
        return vo;
    }
}
