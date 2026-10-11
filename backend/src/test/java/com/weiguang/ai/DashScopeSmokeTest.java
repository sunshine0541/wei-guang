package com.weiguang.ai;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T13 第一步·真实调用冒烟（执行文档 T13 必做项；结论见《实现偏差清单》D22）：
 * - spring-ai-alibaba-starter-dashscope:1.0.0.2 原生协议调 qwen3.8-flash → 400 InvalidParameter "url error"
 *   （本账号该模型不经原生 text-generation 路径路由；同 Key 调 qwen-plus 被 API-Key 模型限制 403）；
 * - 同 Key 经 compatible-mode（OpenAI 兼容）调用 qwen3.8-flash 成功 → 按文档将 ai.real-impl 默认切为 restclient。
 * 本测试走 compatible-mode 真实调用；未配置 DASHSCOPE_API_KEY 时自动跳过，不影响常规构建。
 */
class DashScopeSmokeTest {

    private static final String URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

    @Test
    void realCall() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "未配置 DASHSCOPE_API_KEY，跳过真实调用冒烟");
        String body = "{\"model\":\"qwen3.8-flash\","
                + "\"messages\":[{\"role\":\"user\",\"content\":\"Reply with exactly: smoke-ok\"}],"
                + "\"enable_thinking\":false}";
        long start = System.currentTimeMillis();
        String response = RestClient.create().post().uri(URL)
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);
        long cost = System.currentTimeMillis() - start;
        assertTrue(response != null && response.contains("\"choices\"") && response.contains("\"usage\""),
                "响应结构异常：" + response);
        System.out.println("SMOKE OK cost=" + cost + "ms");
        System.out.println("SMOKE RESP=" + response);
    }
}