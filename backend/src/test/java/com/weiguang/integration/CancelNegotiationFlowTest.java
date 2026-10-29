package com.weiguang.integration;

import com.weiguang.modules.order.dto.CancelConfirmRequest;
import com.weiguang.modules.order.dto.CancelRequest;
import com.weiguang.modules.order.service.CancelService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CancelNegotiationFlowTest extends IntegrationTestBase {

    @Autowired
    private CancelService cancelService;

    @Test
    void negotiateAgreeClosesOrderAndRejectClearsPending() {
        insertUser(1L, "受助者");
        insertUser(2L, "助工");

        // 同意协商取消
        insertOrder(100L, 1L, 2L, "IN_SERVICE", null);
        CancelRequest request = new CancelRequest();
        request.setMode("NEGOTIATE");
        request.setReason("临时要去医院");
        cancelService.cancel(1L, 100L, request);
        assertEquals("REQUESTER", jdbc.queryForObject(
                "SELECT cancel_pending_by FROM help_order WHERE id = 100", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 2 AND type = 'CANCEL_REQUEST'", Integer.class));

        CancelConfirmRequest agree = new CancelConfirmRequest();
        agree.setAgree(true);
        cancelService.cancelConfirm(2L, 100L, agree);
        assertEquals("CANCELLED", status(100L));
        assertNull(jdbc.queryForObject(
                "SELECT cancel_pending_by FROM help_order WHERE id = 100", String.class));

        // 拒绝协商取消：订单保持服务中，挂起字段清空，发起方收到通知
        insertOrder(101L, 1L, 2L, "IN_SERVICE", null);
        request.setMode("NEGOTIATE");
        request.setReason("想换个时间");
        cancelService.cancel(2L, 101L, request);
        assertEquals("HELPER", jdbc.queryForObject(
                "SELECT cancel_pending_by FROM help_order WHERE id = 101", String.class));

        CancelConfirmRequest reject = new CancelConfirmRequest();
        reject.setAgree(false);
        cancelService.cancelConfirm(1L, 101L, reject);
        assertEquals("IN_SERVICE", status(101L));
        assertNull(jdbc.queryForObject(
                "SELECT cancel_pending_by FROM help_order WHERE id = 101", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification WHERE user_id = 2 AND type = 'CANCEL_REJECTED'", Integer.class));
    }

    private String status(long orderId) {
        return jdbc.queryForObject("SELECT status FROM help_order WHERE id = ?", String.class, orderId);
    }
}
