package com.weiguang.ai;

import com.weiguang.ai.dto.ModerationResult;
import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.ai.impl.MockAiService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 能力一/二/三/四的 Mock 行为回归（详设 §5.5、§15.1.6）。
 * 纯单元测试，不依赖数据库与网络。
 */
class MockAiServiceTest {

    private final MockAiService ai = new MockAiService();

    @Test
    void understandMapsCommonDescriptions() {
        UnderstandHelpResult buy = ai.understandHelp("帮我去药店买盒降压药");
        assertEquals("HOME_VISIT", buy.getOrderType());
        assertFalse(buy.isDegraded());

        UnderstandHelpResult phone = ai.understandHelp("教我用手机发照片");
        assertEquals("REMOTE", phone.getOrderType());

        UnderstandHelpResult walk = ai.understandHelp("陪我下楼走一走");
        assertEquals("ACCOMPANY", walk.getOrderType());
    }

    @Test
    void fraudReviewIsBidirectional() {
        ReviewResult helperAsksCode = ai.reviewChat("把验证码发我", "HELPER_TO_REQUESTER");
        assertEquals("HIGH", helperAsksCode.getRiskLevel());

        ReviewResult requesterAsksAdvance = ai.reviewChat("您先帮我垫付一下，回头我给您现金", "REQUESTER_TO_HELPER");
        assertEquals("HIGH", requesterAsksAdvance.getRiskLevel());

        ReviewResult normalBankTrip = ai.reviewChat("陪我去银行取钱", "REQUESTER_TO_HELPER");
        assertEquals("NONE", normalBankTrip.getRiskLevel());

        ReviewResult noMoneyNeeded = ai.reviewChat("东西我放门口了，不用给我钱", "HELPER_TO_REQUESTER");
        assertEquals("NONE", noMoneyNeeded.getRiskLevel());
    }

    @Test
    void moderationFlagsRiskyContent() {
        ModerationResult gambling = ai.moderateContent("POST", "有想一起赌博的吗");
        assertEquals("RISKY", gambling.getRiskLevel());
        assertFalse(gambling.isDegraded());

        ModerationResult normal = ai.moderateContent("POST", "今天楼下的桂花开了");
        assertEquals("SAFE", normal.getRiskLevel());

        ModerationResult advance = ai.moderateContent("COMMENT", "您先帮我垫付一下");
        assertEquals("RISKY", advance.getRiskLevel());
    }

    @Test
    void thankLetterFallbackIsNotEmpty() {
        String draft = ai.draftThankLetter("陪我去医院拿药", "李秀英", "陈志远").getLetterDraft();
        assertTrue(draft.contains("陈志远"));
        assertTrue(draft.length() <= 200);
    }
}
