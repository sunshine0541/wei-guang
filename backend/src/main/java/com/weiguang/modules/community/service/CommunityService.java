package com.weiguang.modules.community.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.community.dto.CommentCreateRequest;
import com.weiguang.modules.community.dto.CommentVO;
import com.weiguang.modules.community.dto.CommunitySummaryVO;
import com.weiguang.modules.community.dto.PostCreateRequest;
import com.weiguang.modules.community.dto.PostCreateVO;
import com.weiguang.modules.community.dto.PostVO;
import com.weiguang.modules.community.dto.ReportCreateRequest;
import com.weiguang.modules.community.entity.CommunityComment;
import com.weiguang.modules.community.entity.CommunityLike;
import com.weiguang.modules.community.entity.CommunityPost;
import com.weiguang.modules.community.mapper.CommunityCommentMapper;
import com.weiguang.modules.community.mapper.CommunityLikeMapper;
import com.weiguang.modules.community.mapper.CommunityPostMapper;
import com.weiguang.modules.incentive.dto.HomeSummaryVO;
import com.weiguang.modules.incentive.service.HonorService;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区 2.0 用户端（详设 §15.1）：顶部三块、动态列表/详情、发帖/删帖、点赞、一级评论、举报。
 * 可见性：非 PUBLISHED 只对作者本人可见；列表只返回 PUBLISHED。
 */
@Service
public class CommunityService {

    private static final String CATEGORY_NORMAL = ContentModerationService.CATEGORY_NORMAL;
    private static final String CATEGORY_STORY = ContentModerationService.CATEGORY_STORY;
    private static final String CATEGORY_OFFICIAL = ContentModerationService.CATEGORY_OFFICIAL;
    private static final String STATUS_PENDING_REVIEW = ContentModerationService.STATUS_PENDING_REVIEW;
    private static final String STATUS_PENDING_MANUAL = ContentModerationService.STATUS_PENDING_MANUAL;
    private static final String STATUS_PUBLISHED = ContentModerationService.STATUS_PUBLISHED;
    private static final String STATUS_DELETED = ContentModerationService.STATUS_DELETED;
    private static final int TITLE_MAX = 50;
    private static final int IMAGE_URL_MAX = 1000;

    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final CommunityLikeMapper likeMapper;
    private final UserProfileMapper userProfileMapper;
    private final UserMapper userMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final ReportMapper reportMapper;
    private final HonorService honorService;
    private final ConfigService configService;
    private final ContentModerationService moderationService;

    public CommunityService(CommunityPostMapper postMapper,
                            CommunityCommentMapper commentMapper,
                            CommunityLikeMapper likeMapper,
                            UserProfileMapper userProfileMapper,
                            UserMapper userMapper,
                            HelpOrderMapper helpOrderMapper,
                            ReportMapper reportMapper,
                            HonorService honorService,
                            ConfigService configService,
                            ContentModerationService moderationService) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.likeMapper = likeMapper;
        this.userProfileMapper = userProfileMapper;
        this.userMapper = userMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.reportMapper = reportMapper;
        this.honorService = honorService;
        this.configService = configService;
        this.moderationService = moderationService;
    }

    // ---------- CM1 顶部三块 ----------

    public CommunitySummaryVO summary() {
        HomeSummaryVO honor = honorService.summary();
        CommunitySummaryVO vo = new CommunitySummaryVO();
        vo.setStars(honor.getHonorTop10());
        // 状态合法值为 NORMAL（不是 ACTIVE）；RESTING 为 v1.3 前的历史常亮态，一并计入"已成为微光"
        vo.setMicroLightCount(userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .in(User::getLightState, "STEADY", "RESTING")
                .eq(User::getStatus, "NORMAL")));
        long todayOrderCount = helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, "PENDING")
                .ge(HelpOrder::getCreatedAt, LocalDate.now().atStartOfDay()));
        vo.setTodayOrderCount(todayOrderCount);
        List<HelpOrder> today = helpOrderMapper.selectList(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, "PENDING")
                .ge(HelpOrder::getCreatedAt, LocalDate.now().atStartOfDay())
                .orderByDesc(HelpOrder::getCreatedAt)
                .last("LIMIT 3"));
        List<CommunitySummaryVO.TodayOrderVO> todayVOs = new ArrayList<>();
        for (HelpOrder order : today) {
            CommunitySummaryVO.TodayOrderVO item = new CommunitySummaryVO.TodayOrderVO();
            item.setId(order.getId());
            item.setTitle(order.getTitle());
            item.setOrderType(order.getOrderType());
            item.setUrgentFlag(order.getUrgentFlag());
            item.setCreatedAt(order.getCreatedAt());
            todayVOs.add(item);
        }
        vo.setTodayOrders(todayVOs);
        return vo;
    }

    // ---------- CM2/CM4/CM6 查询 ----------

    public PageVO<PostVO> list(Long uid, String tab, Long page, Long pageSize) {
        String category = switch (tab == null ? "" : tab) {
            case "NEIGHBOR" -> CATEGORY_NORMAL;
            case "STORY" -> CATEGORY_STORY;
            case "OFFICIAL" -> CATEGORY_OFFICIAL;
            default -> null;
        };
        Page<CommunityPost> result = postMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<CommunityPost>lambdaQuery()
                        .eq(CommunityPost::getStatus, STATUS_PUBLISHED)
                        .eq(category != null, CommunityPost::getCategory, category)
                        .orderByDesc(CommunityPost::getCreatedAt)
                        .orderByDesc(CommunityPost::getId));
        Set<Long> liked = likedSet(uid, result.getRecords());
        return PageVO.of(result.convert(post -> toVO(post, uid, false, liked)));
    }

    public PageVO<PostVO> mine(Long uid, Long page, Long pageSize) {
        Page<CommunityPost> result = postMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<CommunityPost>lambdaQuery()
                        .eq(CommunityPost::getAuthorId, uid)
                        .ne(CommunityPost::getStatus, STATUS_DELETED)
                        .orderByDesc(CommunityPost::getCreatedAt)
                        .orderByDesc(CommunityPost::getId));
        Set<Long> liked = likedSet(uid, result.getRecords());
        return PageVO.of(result.convert(post -> toVO(post, uid, true, liked)));
    }

    public PostVO detail(Long uid, Long postId) {
        CommunityPost post = requirePost(postId);
        if (STATUS_DELETED.equals(post.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        boolean author = uid != null && uid.equals(post.getAuthorId());
        if (!STATUS_PUBLISHED.equals(post.getStatus()) && !author) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return toVO(post, uid, author, likedSet(uid, List.of(post)));
    }

    // ---------- CM3/CM5 发布与删除 ----------

    public PostCreateVO create(Long uid, PostCreateRequest request) {
        String category = request.getCategory();
        if (!CATEGORY_NORMAL.equals(category) && !CATEGORY_STORY.equals(category)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String content = request.getContent() == null ? "" : request.getContent().trim();
        int maxLength = configService.getInt("community.post_max_length", 500);
        if (content.isEmpty() || content.length() > maxLength) {
            throw new BizException(content.length() > maxLength
                    ? ErrorCode.CONTENT_TOO_LONG : ErrorCode.PARAM_INVALID);
        }
        String title = request.getTitle() == null ? null : request.getTitle().trim();
        if (title != null && title.length() > TITLE_MAX) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        String images = imagesJson(request.getImages());

        List<String> blockHits = moderationService.blockHits(content + (title == null ? "" : title));
        CommunityPost post = new CommunityPost();
        post.setAuthorId(uid);
        post.setCategory(category);
        post.setTitle(title == null || title.isEmpty() ? null : title);
        post.setContent(content);
        post.setImages(images);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setReviewDegraded(0);
        post.setCreatedAt(LocalDateTime.now());
        if (blockHits.isEmpty()) {
            post.setStatus(STATUS_PENDING_REVIEW);
            postMapper.insert(post);
            moderationService.reviewPostAsync(post.getId(), uid, content + (title == null ? "" : title));
        } else {
            post.setStatus(STATUS_PENDING_MANUAL);
            post.setReviewReason("本地命中：" + String.join("、", blockHits));
            postMapper.insert(post);
        }
        return new PostCreateVO(post.getId(), post.getStatus());
    }

    public void delete(Long uid, Long postId) {
        CommunityPost post = requirePost(postId);
        if (!uid.equals(post.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (STATUS_DELETED.equals(post.getStatus())) {
            return;
        }
        postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .ne(CommunityPost::getStatus, STATUS_DELETED)
                .set(CommunityPost::getStatus, STATUS_DELETED)
                .set(CommunityPost::getDeletedAt, LocalDateTime.now()));
    }

    // ---------- CM7/CM8 点赞 ----------

    public void like(Long uid, Long postId) {
        CommunityPost post = requirePublished(postId);
        CommunityLike like = new CommunityLike();
        like.setPostId(postId);
        like.setUserId(uid);
        like.setCreatedAt(LocalDateTime.now());
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            return;
        }
        postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .setSql("like_count = like_count + 1"));
    }

    public void unlike(Long uid, Long postId) {
        requirePost(postId);
        int rows = likeMapper.delete(Wrappers.<CommunityLike>lambdaQuery()
                .eq(CommunityLike::getPostId, postId)
                .eq(CommunityLike::getUserId, uid));
        if (rows > 0) {
            postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                    .eq(CommunityPost::getId, postId)
                    .gt(CommunityPost::getLikeCount, 0)
                    .setSql("like_count = like_count - 1"));
        }
    }

    // ---------- CM9–CM11 评论 ----------

    public PageVO<CommentVO> comments(Long uid, Long postId, Long page, Long pageSize) {
        CommunityPost post = requirePost(postId);
        boolean author = uid != null && uid.equals(post.getAuthorId());
        if (!STATUS_PUBLISHED.equals(post.getStatus()) && !author) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        Page<CommunityComment> result = commentMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<CommunityComment>lambdaQuery()
                        .eq(CommunityComment::getPostId, postId)
                        .eq(CommunityComment::getStatus, STATUS_PUBLISHED)
                        .orderByAsc(CommunityComment::getCreatedAt)
                        .orderByAsc(CommunityComment::getId));
        Map<Long, UserProfile> profiles = profiles(result.getRecords().stream()
                .map(CommunityComment::getAuthorId).toList());
        return PageVO.of(result.convert(comment -> toCommentVO(comment, profiles)));
    }

    public void createComment(Long uid, Long postId, CommentCreateRequest request) {
        requirePublished(postId);
        String content = request.getContent() == null ? "" : request.getContent().trim();
        int maxLength = configService.getInt("community.comment_max_length", 200);
        if (content.isEmpty() || content.length() > maxLength) {
            throw new BizException(content.length() > maxLength
                    ? ErrorCode.CONTENT_TOO_LONG : ErrorCode.PARAM_INVALID);
        }
        if (!moderationService.blockHits(content).isEmpty()) {
            throw new BizException(ErrorCode.CONTENT_BLOCKED);
        }
        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setAuthorId(uid);
        comment.setContent(content);
        comment.setStatus(STATUS_PENDING_REVIEW);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);
        moderationService.reviewCommentAsync(comment.getId(), uid, postId, content);
    }

    public void deleteComment(Long uid, Long commentId) {
        CommunityComment comment = commentMapper.selectById(commentId);
        if (comment == null || !uid.equals(comment.getAuthorId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (STATUS_DELETED.equals(comment.getStatus())) {
            return;
        }
        boolean wasPublished = STATUS_PUBLISHED.equals(comment.getStatus());
        commentMapper.update(null, Wrappers.<CommunityComment>lambdaUpdate()
                .eq(CommunityComment::getId, commentId)
                .ne(CommunityComment::getStatus, STATUS_DELETED)
                .set(CommunityComment::getStatus, STATUS_DELETED));
        if (wasPublished) {
            postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                    .eq(CommunityPost::getId, comment.getPostId())
                    .gt(CommunityPost::getCommentCount, 0)
                    .setSql("comment_count = comment_count - 1"));
        }
    }

    // ---------- CM12 举报 ----------

    public void report(Long uid, ReportCreateRequest request) {
        String targetType = request.getTargetType();
        if (!"POST".equals(targetType) && !"COMMENT".equals(targetType)) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty() || reason.length() > 200) {
            throw new BizException(reason.length() > 200 ? ErrorCode.CONTENT_TOO_LONG : ErrorCode.PARAM_INVALID);
        }
        Long targetUserId;
        if ("POST".equals(targetType)) {
            CommunityPost post = requirePost(request.getTargetId());
            targetUserId = post.getAuthorId();
        } else {
            CommunityComment comment = commentMapper.selectById(request.getTargetId());
            if (comment == null || STATUS_DELETED.equals(comment.getStatus())) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            targetUserId = comment.getAuthorId();
        }
        Long exists = reportMapper.selectCount(Wrappers.<Report>lambdaQuery()
                .eq(Report::getReporterId, uid)
                .eq(Report::getTargetType, targetType)
                .eq(Report::getTargetId, request.getTargetId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        Report report = new Report();
        report.setTargetType(targetType);
        report.setTargetId(request.getTargetId());
        report.setTargetUserId(targetUserId);
        report.setReporterId(uid);
        report.setType("CONTENT_REPORT");
        report.setEvidence(reason);
        report.setStatus("PENDING");
        report.setCreatedAt(LocalDateTime.now());
        reportMapper.insert(report);
    }

    // ---------- 内部 ----------

    private CommunityPost requirePost(Long postId) {
        CommunityPost post = postId == null ? null : postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return post;
    }

    private CommunityPost requirePublished(Long postId) {
        CommunityPost post = requirePost(postId);
        if (!STATUS_PUBLISHED.equals(post.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return post;
    }

    private String imagesJson(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        int max = configService.getInt("community.post_image_max", 3);
        if (images.size() > max) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        for (String url : images) {
            if (url == null || !url.startsWith("/files/")) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
        }
        String json = JsonUtils.toJson(images);
        return json.length() > IMAGE_URL_MAX ? null : json;
    }

    private Set<Long> likedSet(Long uid, List<CommunityPost> posts) {
        Set<Long> liked = new HashSet<>();
        if (uid == null || posts.isEmpty()) {
            return liked;
        }
        List<Long> ids = posts.stream().map(CommunityPost::getId).toList();
        List<CommunityLike> likes = likeMapper.selectList(Wrappers.<CommunityLike>lambdaQuery()
                .eq(CommunityLike::getUserId, uid)
                .in(CommunityLike::getPostId, ids));
        likes.forEach(like -> liked.add(like.getPostId()));
        return liked;
    }

    private Map<Long, UserProfile> profiles(List<Long> userIds) {
        Map<Long, UserProfile> map = new HashMap<>();
        List<Long> distinct = userIds.stream().filter(id -> id != null && id != 0L).distinct().toList();
        if (distinct.isEmpty()) {
            return map;
        }
        userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                .in(UserProfile::getUserId, distinct)).forEach(profile -> map.put(profile.getUserId(), profile));
        return map;
    }

    private PostVO toVO(CommunityPost post, Long uid, boolean author, Set<Long> liked) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setAuthorId(post.getAuthorId());
        vo.setAuthorNickname("微光小助手");
        vo.setCategory(post.getCategory());
        vo.setTitle(post.getTitle());
        vo.setContent(post.getContent());
        vo.setImages(JsonUtils.toStringList(post.getImages()));
        vo.setStatus(post.getStatus());
        vo.setLikeCount(post.getLikeCount() == null ? 0 : post.getLikeCount());
        vo.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
        vo.setLiked(liked.contains(post.getId()));
        vo.setCreatedAt(post.getCreatedAt());
        if (post.getAuthorId() != null && post.getAuthorId() != 0L) {
            UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                    .eq(UserProfile::getUserId, post.getAuthorId())
                    .last("LIMIT 1"));
            vo.setAuthorNickname(profile == null || profile.getNickname() == null
                    ? "邻居" : profile.getNickname());
            vo.setAuthorAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        }
        if (author) {
            vo.setReviewReason(post.getReviewReason());
        }
        return vo;
    }

    private CommentVO toCommentVO(CommunityComment comment, Map<Long, UserProfile> profiles) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setAuthorId(comment.getAuthorId());
        vo.setContent(comment.getContent());
        vo.setCreatedAt(comment.getCreatedAt());
        UserProfile profile = profiles.get(comment.getAuthorId());
        vo.setAuthorNickname(profile == null || profile.getNickname() == null ? "邻居" : profile.getNickname());
        vo.setAuthorAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        return vo;
    }
}
