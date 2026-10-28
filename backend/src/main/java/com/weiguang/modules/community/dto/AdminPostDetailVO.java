package com.weiguang.modules.community.dto;

import com.weiguang.modules.report.dto.AdminReportVO;
import lombok.Data;

import java.util.List;

/**
 * 管理端动态详情：动态 + 评论 + 关联举报。
 */
@Data
public class AdminPostDetailVO {

    private AdminPostVO post;

    private List<CommentVO> comments;

    private List<AdminReportVO> reports;
}
