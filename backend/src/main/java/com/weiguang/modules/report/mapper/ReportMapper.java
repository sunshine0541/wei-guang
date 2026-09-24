package com.weiguang.modules.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.report.entity.Report;
import org.apache.ibatis.annotations.Mapper;

/**
 * 防诈高危上报 Mapper。
 */
@Mapper
public interface ReportMapper extends BaseMapper<Report> {
}
