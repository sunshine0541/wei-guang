package com.weiguang.ai.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.ai.AiSanitizer;
import com.weiguang.config.BizMetrics;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * RestClient 直连实现（详设 §5.1 兜底；T13 冒烟后为默认，见《实现偏差清单》D22）：
 * Spring RestClient 直连 https://dashscope.aliyuncs.com/compatible-mode/v1（OpenAI 兼容），复用同一 DASHSCOPE_API_KEY。
 * 超时与重试由公共管线按 ai.timeout_ms / ai.retry 控制（本类负责每次调用的传输超时）。
 */
@Service
public class RestClientAiService extends AbstractRealAiService {

    private static final String CHAT_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    private final String apiKey;

    public RestClientAiService(AiSanitizer sanitizer, MockAiService mockFallback, ConfigService configService,
                               BizMetrics bizMetrics, ObjectMapper objectMapper,
                               @Value("${DASHSCOPE_API_KEY:}") String apiKey) {
        super(sanitizer, mockFallback, configService, bizMetrics, objectMapper);
        this.apiKey = apiKey;
    }

    @Override
    protected ModelCallResult invokeModel(String systemPrompt, String userPrompt, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 DASHSCOPE_API_KEY");
        }
        int timeoutMs = configService.getInt("ai.timeout_ms", 5000);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                "enable_thinking", configService.getBool("ai.thinking", false));
        try {
            String response = RestClient.builder()
                    .requestFactory(factory)
                    .build()
                    .post()
                    .uri(CHAT_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(objectMapper.writeValueAsString(body))
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            String text = root.path("choices").path(0).path("message").path("content").asText("");
            if (text.isBlank()) {
                throw new IllegalStateException("模型回复为空");
            }
            JsonNode usage = root.path("usage");
            return new ModelCallResult(text,
                    usage.has("prompt_tokens") ? usage.get("prompt_tokens").asInt() : null,
                    usage.has("completion_tokens") ? usage.get("completion_tokens").asInt() : null);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("RestClient 调用失败：" + e.getClass().getSimpleName(), e);
        }
    }
}