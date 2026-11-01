package com.weiguang.modules.report.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.community.entity.CommunityComment;
import com.weiguang.modules.community.entity.CommunityPost;
import com.weiguang.modules.community.mapper.CommunityCommentMapper;
import com.weiguang.modules.community.mapper.CommunityPostMapper;
import com.weiguang.modules.incentive.service.CreditService;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.dto.ReportHandleRequest;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 防诈上报（详设 §2.20；管理端列表/处置见 §3.4）。
 */
@Slf4j
@Service
public class ReportService {

    private static final int NOTE_MAX = 200;

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;
    private final NotifyService notifyService;
    private final CreditService creditService;
    private final CommunityPostMapper communityPostMapper;
    private final CommunityCommentMapper communityCommentMapper;

    public ReportService(ReportMapper reportMapper,
                         UserMapper userMapper,
                         NotifyService notifyService,
                         CreditService creditService,
                         CommunityPostMapper communityPostMapper,
                         CommunityCommentMapper communityCommentMapper) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
        this.notifyService = notifyService;
        this.creditService = creditService;
        this.communityPostMapper = communityPostMapper;
        this.communityCommentMapper = communityCommentMapper;
    }

    /** 管理端上报列表（状态筛选可选，按时间倒序） */
    public PageVO<AdminReportVO> adminList(String status, Long page, Long pageSize) {
        Page<Report> result = reportMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<Report>lambdaQuery()
                        .eq(status != null && !status.isBlank(), Report::getStatus, status)
                        .orderByDesc(Report::getId));

        Set<Long> postIds = result.getRecords().stream()
                .filter(r -> "POST".equals(r.getTargetType()))
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
        Set<Long> commentIds = result.getRecords().stream()
                .filter(r -> "COMMENT".equals(r.getTargetType()))
                .map(Report::getTargetId)
                .collect(Collectors.toSet());
        Map<Long, CommunityComment> comments = commentIds.isEmpty() ? Map.of()
                : communityCommentMapper.selectBatchIds(commentIds).stream()
                .collect(Collectors.toMap(CommunityComment::getId, Function.identity()));
        postIds.addAll(comments.values().stream().map(CommunityComment::getPostId).collect(Collectors.toSet()));
        Map<Long, CommunityPost> posts = postIds.isEmpty() ? Map.of()
                : communityPostMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(CommunityPost::getId, Function.identity()));

        return PageVO.of(result.convert(report -> toVO(report, posts, comments)));
    }

    /**
     * POST /admin/reports/{id}/handle 处置（详设 §3.4）：
     * 确认违规/判定误报 + 备注 + 联动（微光值扣减或封禁）+ 通知（ADMIN_ACTION）；
     * 条件更新防重复处置（影响 0 行 → 40003）。
     */
    @Transactional
    public void handle(Long adminId, Long id, ReportHandleRequest request) {
        String result = request == null ? null : request.getResult();
        String note = request == null ? null : request.getNote();
        String action = request == null || request.getAction() == null || request.getAction().isBlank()
                ? "NONE" : request.getAction();
        Integer creditDelta = request == null ? null : request.getCreditDelta();
        if (!"CONFIRMED".equals(result) && !"REJECTED".equals(result)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (note == null || note.isBlank() || note.length() > NOTE_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (!"NONE".equals(action) && !"CREDIT_MINUS".equals(action) && !"BAN".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if ("REJECTED".equals(result) && !"NONE".equals(action)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if ("CREDIT_MINUS".equals(action)
                && (creditDelta == null || (creditDelta != -10 && creditDelta != -20))) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        Report report = reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        int rows = reportMapper.update(null, Wrappers.<Report>lambdaUpdate()
                .eq(Report::getId, id)
                .eq(Report::getStatus, "PENDING")
                .set(Report::getStatus, result)
                .set(Report::getHandlerId, adminId)
                .set(Report::getHandleNote, note)
                .set(Report::getAction, action)
                .set(Report::getHandledAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        if (report.getReporterId() != null) {
            String jumpType = "NONE";
            Long jumpId = null;
            if ("POST".equals(report.getTargetType())) {
                jumpType = "POST";
                jumpId = report.getTargetId();
            } else if ("COMMENT".equals(report.getTargetType())) {
                CommunityComment comment = communityCommentMapper.selectById(report.getTargetId());
                if (comment != null) {
                    jumpType = "POST";
                    jumpId = comment.getPostId();
                }
            } else if ("ORDER".equals(report.getTargetType())) {
                jumpType = "ORDER";
                jumpId = report.getOrderId();
            }
            notifyService.reportResult(report.getReporterId(), result, jumpType, jumpId);
        }

        Long target = report.getTargetUserId();
        if ("CONFIRMED".equals(result) && target != null && !"NONE".equals(action)) {
            if ("CREDIT_MINUS".equals(action)) {
                applyCreditDelta(target, creditDelta, adminId, note);
            } else {
                userMapper.update(null, Wrappers.<User>lambdaUpdate()
                        .eq(User::getId, target)
                        .ne(User::getStatus, "CANCELLED")
                        .set(User::getStatus, "BANNED"));
            }
            notifyService.adminAction(target);
        }
        log.info("上报处置：report={} result={} action={} admin={}", id, result, action, adminId);
    }

    /** 联动扣减：微光值（原子更新 + 明细） */
    private void applyCreditDelta(Long userId, int delta, Long adminId, String note) {
        creditService.apply(userId, delta, "ADMIN_ADJUST", null, adminId, "上报处置：" + note);
    }

    private AdminReportVO toVO(Report report, Map<Long, CommunityPost> posts,
                               Map<Long, CommunityComment> comments) {
        AdminReportVO vo = new AdminReportVO();
        vo.setId(report.getId());
        vo.setOrderId(report.getOrderId());
        vo.setMessageId(report.getMessageId());
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(report.getTargetId());
        vo.setTargetUserId(report.getTargetUserId());
        vo.setReporterId(report.getReporterId());
        vo.setType(report.getType());
        vo.setEvidence(report.getEvidence());
        vo.setStatus(report.getStatus());
        vo.setAction(report.getAction());
        vo.setHandleNote(report.getHandleNote());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());

        if ("POST".equals(report.getTargetType())) {
            CommunityPost post = posts.get(report.getTargetId());
            if (post != null) {
                vo.setTargetPostId(post.getId());
                vo.setTargetTitle(post.getTitle());
                vo.setTargetContent(post.getContent());
                vo.setTargetStatus(post.getStatus());
            }
        } else if ("COMMENT".equals(report.getTargetType())) {
            CommunityComment comment = comments.get(report.getTargetId());
            if (comment != null) {
                vo.setTargetPostId(comment.getPostId());
                vo.setTargetContent(comment.getContent());
                vo.setTargetStatus(comment.getStatus());
                CommunityPost post = posts.get(comment.getPostId());
                if (post != null) {
                    vo.setTargetTitle(post.getTitle());
                }
            }
        }
        return vo;
    }
}