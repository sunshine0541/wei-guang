package com.weiguang.ai;

import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.ai.impl.MockAiService;
import com.weiguang.ai.impl.RestClientAiService;
import com.weiguang.ai.impl.SpringAiAlibabaAiService;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * AI 实现路由（详设 §5.1）：按 ai.mode（mock/real）与 ai.real-impl（springai/restclient）在每次调用时选择实现。
 * 三实现由配置切换、运行时可改（配置经 ConfigService/J9 热刷新，无需重启）。
 * 注：ai.real-impl 默认 restclient（T13 冒烟结论，见《实现偏差清单》D22）。
 */
@Component
@Primary
public class AiServiceRouter implements AiService {

    private final MockAiService mockAiService;
    private final SpringAiAlibabaAiService springAiAlibabaAiService;
    private final RestClientAiService restClientAiService;
    private final ConfigService configService;

    public AiServiceRouter(MockAiService mockAiService,
                           SpringAiAlibabaAiService springAiAlibabaAiService,
                           RestClientAiService restClientAiService,
                           ConfigService configService) {
        this.mockAiService = mockAiService;
        this.springAiAlibabaAiService = springAiAlibabaAiService;
        this.restClientAiService = restClientAiService;
        this.configService = configService;
    }

    @Override
    public UnderstandHelpResult understandHelp(String text) {
        return delegate().understandHelp(text);
    }

    @Override
    public ThankLetterDraftResult draftThankLetter(String orderTitle, String requesterName, String helperName) {
        return delegate().draftThankLetter(orderTitle, requesterName, helperName);
    }

    private AiService delegate() {
        if (!"real".equals(configService.get("ai.mode", "mock"))) {
            return mockAiService;
        }
        return "springai".equals(configService.get("ai.real-impl", "restclient"))
                ? springAiAlibabaAiService : restClientAiService;
    }
}