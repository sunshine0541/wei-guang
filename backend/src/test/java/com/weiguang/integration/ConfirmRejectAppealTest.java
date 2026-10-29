package com.weiguang.integration;

import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.appeal.dto.ArbitrateRequest;
import com.weiguang.modules.appeal.service.AppealService;
import com.weiguang.modules.order.dto.ConfirmRequest;
import com.weiguang.modules.order.service.SettleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfirmRejectAppealTest extends IntegrationTestBase {

    @Autowired
    private SettleService settleService;
    @Autowired
    private AppealService appealService;

    @Test
    void confirmRejectAppealCannotBeWithdrawnAndArbitrationCompletes() {
        insertUser(1L, "受助者");
        insertUser(2L, "助工");
        insertOrder(100L, 1L, 2L, "PENDING_CONFIRM", "REQUESTER");

        ConfirmRequest reject = new ConfirmRequest();
        reject.setAgree(false);
        reject.setReason("灯还是没有换好");
        settleService.confirm(2L, 100L, reject);
        assertEquals("APPEALING", jdbc.queryForObject(
                "SELECT status FROM help_order WHERE id = 100", String.class));

        Long appealId = jdbc.queryForObject(
                "SELECT id FROM appeal WHERE order_id = 100 AND type = 'CONFIRM_REJECT' AND status = 'PENDING'",
                Long.class);
        BizException withdrawError = assertThrows(BizException.class,
                () -> appealService.withdraw(2L, appealId));
        assertEquals(ErrorCode.STATE_NOT_ALLOWED, withdrawError.getCode());

        ArbitrateRequest arbitrate = new ArbitrateRequest();
        arbitrate.setResult("TO_COMPLETED");
        arbitrate.setConclusion("经核对聊天记录与完成情况，予以确认完成");
        appealService.arbitrate(99L, appealId, arbitrate);

        assertEquals("COMPLETED", jdbc.queryForObject(
                "SELECT status FROM help_order WHERE id = 100", String.class));
        assertEquals("RESOLVED", jdbc.queryForObject(
                "SELECT status FROM appeal WHERE id = ?", String.class, appealId));
    }
}
