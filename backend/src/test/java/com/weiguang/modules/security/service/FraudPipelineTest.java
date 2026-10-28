package com.weiguang.modules.security.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.config.BizMetrics;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.chat.entity.ChatMessage;
import com.weiguang.modules.chat.mapper.ChatMessageMapper;
import com.weiguang.modules.report.entity.Report;
import com.weiguang.modules.report.mapper.ReportMapper;
import com.weiguang.modules.security.entity.SensitiveWord;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 能力二防诈管线（双向复核 + 强提示分流）单元测试，不依赖数据库。
 */
class FraudPipelineTest {

    static {
        // 单元测试没有 MyBatis 启动流程，需手动初始化 Lambda 列缓存
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ChatMessage.class);
    }

    private SensitiveWordService sensitiveWordService;
    private AiService aiService;
    private ReportMapper reportMapper;
    private WsSessionRegistry wsSessionRegistry;
    private BizMetrics bizMetrics;
    private FraudPipeline pipeline;

    @BeforeEach
    void setUp() {
        sensitiveWordService = mock(SensitiveWordService.class);
        aiService = mock(AiService.class);
        reportMapper = mock(ReportMapper.class);
        wsSessionRegistry = mock(WsSessionRegistry.class);
        bizMetrics = mock(BizMetrics.class);
        pipeline = new FraudPipeline(sensitiveWordService, aiService,
                mock(ChatMessageMapper.class), reportMapper, wsSessionRegistry, bizMetrics);

        SensitiveWord word = new SensitiveWord();
        word.setWord("验证码");
        word.setAction(SensitiveWordService.ACTION_REVIEW);
        when(sensitiveWordService.scan(anyString())).thenReturn(List.of(word));

        ReviewResult high = new ReviewResult();
        high.setRiskLevel("HIGH");
        high.setDegraded(false);
        high.setHitWords(List.of("验证码"));
        when(aiService.reviewChat(any(), any())).thenReturn(high);
    }

    @Test
    void localReviewIsRequiredForBothDirections() {
        FraudPipeline.LocalDecision decision = pipeline.evaluateLocal("把验证码发我");
        assertTrue(decision.reviewRequired());
        assertEquals(FraudPipeline.FLAG_HIGHLIGHT, decision.riskFlag());
        assertEquals(List.of("验证码"), decision.hitWords());
    }

    @Test
    void requesterToHelperHighRiskUsesRequesterAlert() {
        pipeline.reviewAsync(1L, 2L, 10L, 20L, "您先帮我垫付一下", List.of("垫付"),
                FraudPipeline.DIRECTION_REQUESTER_TO_HELPER);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportMapper).insert(captor.capture());
        assertTrue(captor.getValue().getEvidence().contains("REQUESTER_TO_HELPER"));
        assertEquals(10L, captor.getValue().getTargetUserId());

        verify(wsSessionRegistry).pushAsync(eq(20L), any());
        verify(wsSessionRegistry).pushAsync(eq(10L), any());
        verify(bizMetrics).fraudHit();
    }

    @Test
    void degradedAiDoesNotUpgrade() {
        ReviewResult degraded = new ReviewResult();
        degraded.setRiskLevel("HIGH");
        degraded.setDegraded(true);
        when(aiService.reviewChat(any(), any())).thenReturn(degraded);

        pipeline.reviewAsync(1L, 2L, 10L, 20L, "把验证码发我", List.of("验证码"),
                FraudPipeline.DIRECTION_HELPER_TO_REQUESTER);
        verify(reportMapper, org.mockito.Mockito.never()).insert(any(Report.class));
    }
}
