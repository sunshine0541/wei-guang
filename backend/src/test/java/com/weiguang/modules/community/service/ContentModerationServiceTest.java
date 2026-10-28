package com.weiguang.modules.community.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力四内容审核管线（详设 §15.1.6）：RISKY 转人工、SAFE 公开、本地 BLOCK 命中。
 */
class ContentModerationServiceTest {

    static {
        // 单元测试没有 MyBatis 启动流程，需手动初始化 Lambda 列缓存
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, CommunityPost.class);
        TableInfoHelper.initTableInfo(assistant, CommunityComment.class);
    }

    private SensitiveWordService sensitiveWordService;
    private AiService aiService;
    private CommunityPostMapper postMapper;
    private NotifyService notifyService;
    private ConfigService configService;
    private BizMetrics bizMetrics;
    private ContentModerationService service;

    @BeforeEach
    void setUp() {
        sensitiveWordService = mock(SensitiveWordService.class);
        aiService = mock(AiService.class);
        postMapper = mock(CommunityPostMapper.class);
        notifyService = mock(NotifyService.class);
        configService = mock(ConfigService.class);
        bizMetrics = mock(BizMetrics.class);
        service = new ContentModerationService(sensitiveWordService, aiService, postMapper,
                mock(CommunityCommentMapper.class), notifyService, configService, bizMetrics,
                mock(UserProfileMapper.class));

        when(configService.getBool("community.ai_review_enabled", true)).thenReturn(true);
        when(configService.get("ai.mode", "mock")).thenReturn("real");
        when(postMapper.update(org.mockito.ArgumentMatchers.isNull(), any())).thenReturn(1);
        when(postMapper.selectById(any())).thenReturn(pendingPost());
        when(sensitiveWordService.scan(anyString())).thenReturn(List.of());
    }

    @Test
    void riskyContentGoesToManualReview() {
        ModerationResult risky = new ModerationResult();
        risky.setRiskLevel("RISKY");
        risky.setDegraded(false);
        risky.setHitWords(List.of("赌博"));
        risky.setSuggestion("不适合公开展示");
        when(aiService.moderateContent(anyString(), anyString())).thenReturn(risky);

        service.reviewPostAsync(5L, 3L, "有想一起赌博的吗");

        verify(bizMetrics).moderationHit();
        verify(notifyService).postReviewResult(3L, 5L, "PENDING");
    }

    @Test
    void safeContentIsPublished() {
        ModerationResult safe = new ModerationResult();
        safe.setRiskLevel("SAFE");
        safe.setDegraded(false);
        when(aiService.moderateContent(anyString(), anyString())).thenReturn(safe);

        service.reviewPostAsync(5L, 3L, "今天楼下的桂花开了");

        verify(notifyService).postReviewResult(3L, 5L, "APPROVED");
        verify(bizMetrics, never()).moderationHit();
    }

    @Test
    void localBlockWordIsDetected() {
        SensitiveWord block = new SensitiveWord();
        block.setWord("赌博");
        block.setAction(SensitiveWordService.ACTION_BLOCK);
        when(sensitiveWordService.scan("一起去赌博")).thenReturn(List.of(block));

        assertEquals(List.of("赌博"), service.blockHits("一起去赌博"));
    }

    private CommunityPost pendingPost() {
        CommunityPost post = new CommunityPost();
        post.setId(5L);
        post.setAuthorId(3L);
        post.setStatus(ContentModerationService.STATUS_PENDING_REVIEW);
        return post;
    }
}
