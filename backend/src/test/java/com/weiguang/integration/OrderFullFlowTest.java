package com.weiguang.integration;

import com.weiguang.modules.incentive.dto.EvaluationRequest;
import com.weiguang.modules.incentive.dto.LetterRequest;
import com.weiguang.modules.incentive.service.IncentiveService;
import com.weiguang.modules.order.dto.ConfirmRequest;
import com.weiguang.modules.order.service.OrderService;
import com.weiguang.modules.order.service.SettleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderFullFlowTest extends IntegrationTestBase {

    @Autowired
    private OrderService orderService;
    @Autowired
    private SettleService settleService;
    @Autowired
    private IncentiveService incentiveService;

    @Test
    void publishAcceptStartCompleteConfirmEvaluateAndThank() {
        insertUser(1L, "受助者");
        insertUser(2L, "助工");
        insertOrder(100L, 1L, null, "PENDING", null);

        orderService.accept(2L, 100L);
        assertEquals("ACCEPTED", status(100L));
        orderService.start(2L, 100L);
        assertEquals("IN_SERVICE", status(100L));

        settleService.complete(2L, 100L);
        assertEquals("PENDING_CONFIRM", status(100L));

        ConfirmRequest agree = new ConfirmRequest();
        agree.setAgree(true);
        settleService.confirm(1L, 100L, agree);
        assertEquals("COMPLETED", status(100L));

        assertEquals(102, jdbc.queryForObject(
                "SELECT credit_score FROM user WHERE id = 2", Integer.class));
        assertEquals(10, jdbc.queryForObject(
                "SELECT balance_after FROM point_ledger WHERE user_id = 2 ORDER BY id DESC LIMIT 1",
                Integer.class));

        EvaluationRequest evaluation = new EvaluationRequest();
        evaluation.setStars(5);
        evaluation.setAdvice("很细心，灯修好了");
        incentiveService.evaluate(1L, 100L, evaluation);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM evaluation WHERE order_id = 100", Integer.class));

        LetterRequest letter = new LetterRequest();
        letter.setContent("谢谢您，家里一下亮堂了");
        letter.setAiAssisted(false);
        incentiveService.thankLetter(1L, 100L, letter);
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM thank_letter WHERE order_id = 100", Integer.class));

        assertNotNull(incentiveService.storyCard(1L, 100L));
    }

    private String status(long orderId) {
        return jdbc.queryForObject("SELECT status FROM help_order WHERE id = ?", String.class, orderId);
    }
}
