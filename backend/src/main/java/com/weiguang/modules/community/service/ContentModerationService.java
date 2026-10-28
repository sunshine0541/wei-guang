package com.weiguang.modules.community.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ModerationResult;
import com.weiguang.config.BizMetrics;
import com.weiguang.modules.community.entity.CommunityComment;
import com.weiguang.modules.community.entity.CommunityPost;
import com.weiguang.modules.community.mapper.CommunityCommentMapper;
import com.weiguang.modules.community.mapper.CommunityPostMapper;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.security.entity.SensitiveWord;
import com.weiguang.modules.security.service.SensitiveWordService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容审核管线（详设 §15.1.6，能力四独立于防诈）：
 * 本地 BLOCK → 人工；AI SAFE → 公开；AI RISKY → 人工；AI 降级 → 本地 REVIEW 命中转人工，否则放行并标记。
 */
@Slf4j
@Service
public class ContentModerationService {

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_PENDING_MANUAL = "PENDING_MANUAL";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_HIDDEN = "HIDDEN";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DELETED = "DELETED";

    public static final String CATEGORY_NORMAL = "NORMAL";
    public static final String CATEGORY_STORY = "STORY";
    public static final String CATEGORY_OFFICIAL = "OFFICIAL";

    private final SensitiveWordService sensitiveWordService;
    private final AiService aiService;
    private final CommunityPostMapper postMapper;
    private final CommunityCommentMapper commentMapper;
    private final NotifyService notifyService;
    private final ConfigService configService;
    private final BizMetrics bizMetrics;
    private final UserProfileMapper userProfileMapper;

    public ContentModerationService(SensitiveWordService sensitiveWordService,
                                    AiService aiService,
                                    CommunityPostMapper postMapper,
                                    CommunityCommentMapper commentMapper,
                                    NotifyService notifyService,
                                    ConfigService configService,
                                    BizMetrics bizMetrics,
                                    UserProfileMapper userProfileMapper) {
        this.sensitiveWordService = sensitiveWordService;
        this.aiService = aiService;
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.notifyService = notifyService;
        this.configService = configService;
        this.bizMetrics = bizMetrics;
        this.userProfileMapper = userProfileMapper;
    }

    /** 本地 BLOCK 命中词（发布/评论直接转人工） */
    public List<String> blockHits(String text) {
        return sensitiveWordService.scan(text).stream()
                .filter(word -> SensitiveWordService.ACTION_BLOCK.equals(word.getAction()))
                .map(SensitiveWord::getWord)
                .toList();
    }

    private List<String> reviewHits(String text) {
        return sensitiveWordService.scan(text).stream()
                .filter(word -> SensitiveWordService.ACTION_REVIEW.equals(word.getAction()))
                .map(SensitiveWord::getWord)
                .toList();
    }

    /** 异步复核动态（事务外；mock 或无 AI 开关时直接公开） */
    @Async
    public void reviewPostAsync(Long postId, Long authorId, String text) {
        try {
            CommunityPost post = postMapper.selectById(postId);
            if (post == null || !STATUS_PENDING_REVIEW.equals(post.getStatus())) {
                return;
            }
            if (!aiReviewEnabled()) {
                publishPost(postId, authorId, false);
                return;
            }
            ModerationResult result = aiService.moderateContent("POST", text);
            if (result.isDegraded()) {
                if (!reviewHits(text).isEmpty()) {
                    pendingManualPost(postId, authorId, "智能审核暂时不可用，需人工确认");
                } else {
                    publishPost(postId, authorId, true);
                }
                return;
            }
            if ("RISKY".equals(result.getRiskLevel())) {
                bizMetrics.moderationHit();
                pendingManualPost(postId, authorId, summarize(result));
            } else {
                publishPost(postId, authorId, false);
            }
        } catch (Exception e) {
            log.warn("动态审核异常 postId={}：{}", postId, e.getClass().getSimpleName());
        }
    }

    /** 异步复核评论 */
    @Async
    public void reviewCommentAsync(Long commentId, Long authorId, Long postId, String text) {
        try {
            CommunityComment comment = commentMapper.selectById(commentId);
            if (comment == null || !STATUS_PENDING_REVIEW.equals(comment.getStatus())) {
                return;
            }
            if (!aiReviewEnabled()) {
                publishComment(commentId, authorId, postId, false);
                return;
            }
            ModerationResult result = aiService.moderateContent("COMMENT", text);
            if (result.isDegraded()) {
                if (!reviewHits(text).isEmpty()) {
                    pendingManualComment(commentId, authorId, "智能审核暂时不可用，需人工确认");
                } else {
                    publishComment(commentId, authorId, postId, true);
                }
                return;
            }
            if ("RISKY".equals(result.getRiskLevel())) {
                bizMetrics.moderationHit();
                pendingManualComment(commentId, authorId, summarize(result));
            } else {
                publishComment(commentId, authorId, postId, false);
            }
        } catch (Exception e) {
            log.warn("评论审核异常 commentId={}：{}", commentId, e.getClass().getSimpleName());
        }
    }

    private boolean aiReviewEnabled() {
        return configService.getBool("community.ai_review_enabled", true)
                && !"mock".equals(configService.get("ai.mode", "mock"));
    }

    private void publishPost(Long postId, Long authorId, boolean degraded) {
        int rows = postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .eq(CommunityPost::getStatus, STATUS_PENDING_REVIEW)
                .set(CommunityPost::getStatus, STATUS_PUBLISHED)
                .set(CommunityPost::getReviewDegraded, degraded ? 1 : 0));
        if (rows > 0) {
            notifyService.postReviewResult(authorId, postId, "APPROVED");
        }
    }

    private void pendingManualPost(Long postId, Long authorId, String reason) {
        int rows = postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .eq(CommunityPost::getStatus, STATUS_PENDING_REVIEW)
                .set(CommunityPost::getStatus, STATUS_PENDING_MANUAL)
                .set(CommunityPost::getReviewReason, trim(reason)));
        if (rows > 0) {
            notifyService.postReviewResult(authorId, postId, "PENDING");
        }
    }

    private void publishComment(Long commentId, Long authorId, Long postId, boolean degraded) {
        int rows = commentMapper.update(null, Wrappers.<CommunityComment>lambdaUpdate()
                .eq(CommunityComment::getId, commentId)
                .eq(CommunityComment::getStatus, STATUS_PENDING_REVIEW)
                .set(CommunityComment::getStatus, STATUS_PUBLISHED));
        if (rows == 0) {
            return;
        }
        postMapper.update(null, Wrappers.<CommunityPost>lambdaUpdate()
                .eq(CommunityPost::getId, postId)
                .setSql("comment_count = comment_count + 1"));
        notifyComment(postId, authorId);
    }

    private void pendingManualComment(Long commentId, Long authorId, String reason) {
        int rows = commentMapper.update(null, Wrappers.<CommunityComment>lambdaUpdate()
                .eq(CommunityComment::getId, commentId)
                .eq(CommunityComment::getStatus, STATUS_PENDING_REVIEW)
                .set(CommunityComment::getStatus, STATUS_PENDING_MANUAL));
        if (rows > 0) {
            log.info("评论转人工审核 commentId={} reason={}", commentId, trim(reason));
        }
    }

    /** 评论公开后通知动态作者（自评不通知） */
    public void notifyComment(Long postId, Long commenterId) {
        CommunityPost post = postMapper.selectById(postId);
        if (post == null || post.getAuthorId() == null || post.getAuthorId().equals(commenterId)) {
            return;
        }
        if (post.getAuthorId() == 0L) {
            return;
        }
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, commenterId)
                .last("LIMIT 1"));
        String nickname = profile == null || profile.getNickname() == null || profile.getNickname().isBlank()
                ? "邻居" : profile.getNickname();
        notifyService.commentReply(post.getAuthorId(), nickname, postId);
    }

    private String summarize(ModerationResult result) {
        String words = result.getHitWords() == null ? "" : String.join("、", result.getHitWords());
        String suggestion = result.getSuggestion() == null ? "" : result.getSuggestion();
        return trim(words.isEmpty() ? suggestion : "命中：" + words + (suggestion.isEmpty() ? "" : "；" + suggestion));
    }

    private String trim(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > 200 ? text.substring(0, 200) : text;
    }
}
