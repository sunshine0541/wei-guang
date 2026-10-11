package com.weiguang.ai;

import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.ai.impl.RestClientAiService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * T14 检查点·能力二五条边界样例（详设 §5.3 写死）：
 * 直连真实实现（RestClientAiService，绕过 mock 路由），验证真实复核模型判定。
 * 「陪我去银行取钱」→ NONE/WARN；「您自己去柜台取就行，我在旁边等您」→ NONE；
 * 「把验证码发我」/「您的银行卡号告诉我一下」/「这个需要扫码付款，您扫这个码」→ HIGH。
 * 未配置 DASHSCOPE_API_KEY 时自动跳过，不影响常规构建。
 */
@SpringBootTest
class FraudReviewSamplesTest {

    @Autowired
    private RestClientAiService restClientAiService;

    @Test
    void boundarySamples() {
        String key = System.getenv("DASHSCOPE_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "未配置 DASHSCOPE_API_KEY，跳过能力二样例");
        check("陪我去银行取钱", "NONE,WARN");
        check("您自己去柜台取就行，我在旁边等您", "NONE");
        check("把验证码发我", "HIGH");
        check("您的银行卡号告诉我一下", "HIGH");
        check("这个需要扫码付款，您扫这个码", "HIGH");
    }

    private void check(String text, String expect) {
        ReviewResult result = restClientAiService.reviewChat(text, "HELPER_TO_REQUESTER");
        assertFalse(result.isDegraded(), "真实复核不应降级：" + text);
        assertTrue(List.of(expect.split(",")).contains(result.getRiskLevel()),
                "样例判定不符：" + text + " -> " + result.getRiskLevel());
        System.out.println("SAMPLE OK [" + text + "] -> " + result.getRiskLevel() + " hit=" + result.getHitWords());
    }
}