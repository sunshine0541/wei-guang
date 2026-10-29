package com.weiguang.modules.community.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.community.dto.AdminOfficialPostRequest;
import com.weiguang.modules.community.dto.AdminPostDetailVO;
import com.weiguang.modules.community.dto.AdminPostReviewRequest;
import com.weiguang.modules.community.dto.AdminPostVO;
import com.weiguang.modules.community.dto.CommentVO;
import com.weiguang.modules.community.entity.CommunityComment;
import com.weiguang.modules.community.entity.CommunityPost;
import com.weiguang.modules.community.mapper.CommunityCommentMapper;
import com.weiguang.modules.community.mapper.CommunityPostMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.report.dto.AdminReportVO;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 社区 2.0 管理端（详设 §15.1.5）：审核队列、隐藏/恢复/删除、官方发布、评论审核。
 */
@Service
public class AdminCommunityService {

    private static final String PENDING_REVIEW = ContentModerationService.STATUS_PENDING_REVIEW;
    private static final String PENDING_MANUAL = ContentModerationService.STATUS_PENDING_MANUAL;
    private static final String PUBLISHED = ContentModerationService.STATUS_PUBLISHED;
    private static final String HIDDEN = ContentModerationService.STATUS_HIDDEN;
    private static final String REJECTED = ContentModerationService.STATUS_REJECTED;
    private static final String DELETED = ContentModerationService.STATUS_DELETED;
    private static final String OFFICIAL = ContentModerationService.CATEGORY_OFFICIAL;

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final UserProfileMapper userProfileMapper;
    private final ReportMapper reportMapper;
    private final NotifyService notifyService;
    private final ConfigService configService;

    public AdminCommunityService(CommunityPostMapper postMapper,
                                 CommunityCommentMapper commentMapper,
                                 UserProfileMapper userProfileMapper,
                                 ReportMapper reportMapper,
                                 NotifyService notifyService,
                                 ConfigService configService) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userProfileMapper = userProfileMapper;
        this.reportMapper = reportMapper;
        this.notifyService = notifyService;
        this.configService = configService;
    }

    public PageVO<AdminPostVO> list(String status, String category, String keyword, Long page, Long pageSize) {
        Page<CommunityPost> result = postMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<CommunityPost>lambdaQuery()
                        .eq(status != null && !status.isBlank(), CommunityPost::getStatus, status)
                        .eq(category != null && !category.isBlank(), CommunityPost::getCategory, category)
                        .like(keyword != null && !keyword.isBlank(), CommunityPost::getContent, keyword)
                        .orderByDesc(CommunityPost::getId));
        Map<Long, UserProfile> profiles = profiles(result.getRecords().stream()
                .map(CommunityPost::getAuthorId).toList());
        return PageVO.of(result.convert(post -> toVO(post, profiles)));
    }

    public AdminPostDetailVO detail(Long postId) {
        CommunityPost post = requirePost(postId);
        AdminPostDetailVO detail = new AdminPostDetailVO();
        detail.setPost(toVO(post, profiles(List.of(post.getAuthorId()))));
        List<CommunityComment> comments = commentMapper.selectList(Wrappers.<CommunityComment>lambdaQuery()
                .eq(CommunityComment::getPostId, postId)
                .ne(CommunityComment::getStatus, DELETED)
                .orderByAsc(CommunityComment::getCreatedAt));
        detail.setComments(toCommentVOs(comments));
        List<Long> commentIds = comments.stream().map(CommunityComment::getId).toList();
        var reportQuery = Wrappers.<Report>lambdaQuery().orderByDesc(Report::getId);
        if (commentIds.isEmpty()) {
            reportQuery.eq(Report::getTargetType, "POST").eq(Report::getTargetId, postId);
        } else {
            reportQuery.and(w -> w
                    .and(a -> a.eq(Report::getTargetType, "POST").eq(Report::getTargetId, postId))
                    .or(b -> b.eq(Report::getTargetType, "COMMENT").in(Report::getTargetId, commentIds)));
        }
        List<Report> reports = reportMapper.selectList(reportQuery);
        detail.setReports(reports.stream().map(this::toReportVO).toList());
        return detail;
    }

    public void review(Long postId, AdminPostReviewRequest request) {
        CommunityPost post = requirePost(postId);
        String expected = post.getStatus();
        if (!PENDING_MANUAL.equals(expected) && !PENDING_REVIEW.equals(expected)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        boolean approve = "APPROVE".equalsIgnoreCase(request.getResult());
        int rows = postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .eq(CommunityPost::getStatus, expected)
                .set(CommunityPost::getStatus, approve ? PUBLISHED : REJECTED)
                .set(CommunityPost::getReviewReason, trim(request.getNote())));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        notifyService.postReviewResult(post.getAuthorId(), postId, approve ? "APPROVED" : "REJECTED");
    }

    public void hide(Long postId) {
        changeStatus(postId, PUBLISHED, HIDDEN);
    }

    public void restore(Long postId) {
        changeStatus(postId, HIDDEN, PUBLISHED);
    }

    public void delete(Long postId) {
        int rows = postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .ne(CommunityPost::getStatus, DELETED)
                .set(CommunityPost::getStatus, DELETED)
                .set(CommunityPost::getDeletedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
    }

    public Long createOfficial(AdminOfficialPostRequest request) {
        String content = request.getContent() == null ? "" : request.getContent().trim();
        int maxLength = configService.getInt("community.post_max_length", 500);
        if (content.isEmpty() || content.length() > maxLength) {
            throw new BizException(content.length() > maxLength
                    ? ErrorCode.CONTENT_TOO_LONG : ErrorCode.PARAM_INVALID);
        }
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title != null && title.length() > 50) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        CommunityPost post = new CommunityPost();
        post.setAuthorId(0L);
        post.setCategory(OFFICIAL);
        post.setTitle(title == null || title.isEmpty() ? null : title);
        post.setContent(content);
        post.setImages(imagesJson(request.getImages()));
        post.setStatus(PUBLISHED);
        post.setReviewDegraded(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);
        return post.getId();
    }

    public void reviewComment(Long commentId, AdminPostReviewRequest request) {
        CommunityComment comment = requireComment(commentId);
        String expected = comment.getStatus();
        if (!PENDING_MANUAL.equals(expected) && !PENDING_REVIEW.equals(expected)) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        boolean approve = "APPROVE".equalsIgnoreCase(request.getResult());
        int rows = commentMapper.update(null, Wrappers.<CommunityComment>lambdaUpdate()
                .eq(CommunityComment::getId, commentId)
                .eq(CommunityComment::getStatus, expected)
                .set(CommunityComment::getStatus, approve ? PUBLISHED : DELETED));
        if (rows == 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        if (approve) {
            postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                    .eq(CommunityPost::getId, comment.getPostId())
                    .setSql("comment_count = comment_count + 1"));
            notifyCommentReply(comment);
        }
    }

    public void deleteComment(Long commentId) {
        CommunityComment comment = requireComment(commentId);
        boolean wasPublished = PUBLISHED.equals(comment.getStatus());
        int rows = commentMapper.update(null, Wrappers.<CommunityComment>lambdaUpdate()
                .eq(CommunityComment::getId, commentId)
                .ne(CommunityComment::getStatus, DELETED)
                .set(CommunityComment::getStatus, DELETED));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (wasPublished) {
            postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                    .eq(CommunityPost::getId, comment.getPostId())
                    .gt(CommunityPost::getCommentCount, 0)
                    .setSql("comment_count = comment_count - 1"));
        }
    }

    // ---------- 内部 ----------

    private void changeStatus(Long postId, String from, String to) {
        int rows = postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .eq(CommunityPost::getStatus, from)
                .set(CommunityPost::getStatus, to));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
    }

    private void notifyCommentReply(CommunityComment comment) {
        CommunityPost post = postMapper.selectById(comment.getPostId());
        if (post == null || post.getAuthorId() == null || post.getAuthorId() == 0L
                || post.getAuthorId().equals(comment.getAuthorId())) {
            return;
        }
        UserProfile profile = profileOf(comment.getAuthorId());
        String nickname = profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname();
        notifyService.commentReply(post.getAuthorId(), nickname, post.getId());
    }

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = postId == null ? null : postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return post;
    }

    private CommunityComment requireComment(Long commentId) {
        CommunityComment comment = commentId == null ? null : commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return comment;
    }

    private String imagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        int max = configService.getInt("community.post_image_max", 3);
        if (images.size() > max) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String json = JsonUtils.toJson(images);
        return json.length() > 1000 ? null : json;
    }

    private AdminPostVO toVO(CommunityPost post, Map<Long, UserProfile> profiles) {
        AdminPostVO vo = new AdminPostVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getAuthorId());
        vo.setAuthorNickname("微光小助手");
        vo.setCategory(post.getCategory());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setImages(JsonUtils.toStringList(post.getImages()));
        vo.setStatus(post.getStatus());
        vo.setReviewDegraded(post.getReviewDegraded());
        vo.setReviewReason(post.getReviewReason());
        vo.setLikeCount(post.getLikeCount());
        vo.setCommentCount(post.getCommentCount());
        vo.setCreatedAt(post.getCreatedAt());
        if (post.getAuthorId() != null && post.getAuthorId() != 0L) {
            UserProfile profile = profiles.get(post.getAuthorId());
            vo.setAuthorNickname(profile == null || profile.getNickname() == null
                    ? "邻居" : profile.getNickname());
        }
        return vo;
    }

    private Map<Long, UserProfile> profiles(List<Long> userIds) {
        Map<Long, UserProfile> map = new HashMap<>();
        List<Long> distinct = userIds.stream().filter(id -> id != null && id != 0L).distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                .in(UserProfile::getUserId, distinct)).forEach(p -> map.put(p.getUserId(), p));
        return map;
    }

    private List<CommentVO> toCommentVOs(List<CommunityComment> comments) {
        Map<Long, UserProfile> profiles = new HashMap<>();
        List<Long> ids = comments.stream().map(CommunityComment::getAuthorId).distinct().toList();
        if (!ids.isEmpty()) {
            userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                    .in(UserProfile::getUserId, ids)).forEach(p -> profiles.put(p.getUserId(), p));
        }
        List<CommentVO> list = new ArrayList<>();
        for (CommunityComment comment : comments) {
            CommentVO vo = new CommentVO();
            vo.setId(comment.getId());
            vo.setPostId(comment.getPostId());
            vo.setAuthorId(comment.getAuthorId());
            vo.setContent(comment.getContent());
            vo.setCreatedAt(comment.getCreatedAt());
            UserProfile profile = profiles.get(comment.getAuthorId());
            vo.setAuthorNickname(profile == null || profile.getNickname() == null
                    ? "邻居" : profile.getNickname());
            list.add(vo);
        }
        return list;
    }

    private AdminReportVO toReportVO(Report report) {
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
        return vo;
    }

    private UserProfile profileOf(Long uid) {
        return userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, uid)
                .last("LIMIT 1"));
    }

    private String trim(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
