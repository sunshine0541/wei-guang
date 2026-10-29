package com.weiguang.ai.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.ai.AiSanitizer;
import com.weiguang.config.BizMetrics;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Spring AI Alibaba 实现（详设 §5.1）：仅用 spring-ai-alibaba-starter-dashscope 的模型调用能力。
 * 注：T13 冒烟显示本账号 qwen3.8-flash 未经原生协议路由（400 url error），故默认实现为 restclient；
 * 本实现保留，可经 ai.real-impl=springai 切换（见《实现偏差清单》D22）。
 */
@Service
public class SpringAiAlibabaAiService extends AbstractRealAiService {

    private final ObjectProvider<ChatModel> chatModelProvider;

    public SpringAiAlibabaAiService(AiSanitizer sanitizer, MockAiService mockFallback, ConfigService configService,
                                    BizMetrics bizMetrics, ObjectMapper objectMapper,
                                    ObjectProvider<ChatModel> chatModelProvider) {
        super(sanitizer, mockFallback, configService, bizMetrics, objectMapper);
        this.chatModelProvider = chatModelProvider;
    }

    @Override
    protected ModelCallResult invokeModel(String systemPrompt, String userPrompt, String model) {
        ChatModel chatModel = chatModelProvider.getIfAvailable();
        if (chatModel == null) {
            throw new IllegalStateException("DashScope ChatModel 未自动配置");
        }
        ChatResponse response = chatModel.call(new Prompt(
                List.of(new SystemMessage(systemPrompt), new UserMessage(userPrompt)),
                DashScopeChatOptions.builder()
                        .withModel(model)
                        .withEnableThinking(configService.getBool("ai.thinking", false))
                        .build()));
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new IllegalStateException("模型回复为空");
        }
        String text = response.getResult().getOutput().getText();
        Integer promptTokens = null;
        Integer completionTokens = null;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
            promptTokens = response.getMetadata().getUsage().getPromptTokens();
            completionTokens = response.getMetadata().getUsage().getCompletionTokens();
        }
        return new ModelCallResult(text, promptTokens, completionTokens);
    }
}