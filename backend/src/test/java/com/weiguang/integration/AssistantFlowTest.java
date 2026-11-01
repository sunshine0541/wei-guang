package com.weiguang.integration;

import com.weiguang.modules.assistant.dto.AssistantChatRequest;
import com.weiguang.modules.assistant.dto.AssistantChatResponse;
import com.weiguang.modules.assistant.dto.AssistantConfirmRequest;
import com.weiguang.modules.assistant.service.AssistantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssistantFlowTest extends IntegrationTestBase {

    @Autowired
    private AssistantService assistantService;

    @Test
    void commandWithoutDescriptionDoesNotPublish() {
        insertUser(1L, "李秀英");
        jdbc.update("UPDATE user_profile SET emergency_contact_name = '王阿姨', "
                + "emergency_contact_phone = '13800000000' WHERE user_id = 1");
        jdbc.update("INSERT INTO address_book (user_id, label, area_text, detail_text, is_default) "
                + "VALUES (1, '家', '梧桐苑小区 7 号楼', '2 单元 603', 1)");

        AssistantChatRequest command = new AssistantChatRequest();
        command.setMessage("帮我发布求助");
        AssistantChatResponse ask = assistantService.chat(1L, command);
        assertNull(ask.getDraft());
        assertNull(ask.getConfirmToken());

        AssistantChatRequest time = new AssistantChatRequest();
        time.setSessionId(ask.getSessionId());
        time.setMessage("时间选好了，继续发布");
        time.setTimeExpect("THIS_WEEK");
        AssistantChatResponse stillAsk = assistantService.chat(1L, time);
        assertEquals("COLLECTING", stillAsk.getAction());
        assertNull(stillAsk.getDraft());
        assertNull(stillAsk.getConfirmToken());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM help_order", Integer.class));
    }

    @Test
    void guideThenPublishAfterConfirm() {
        insertUser(1L, "李秀英");
        jdbc.update("UPDATE user_profile SET emergency_contact_name = '王阿姨', "
                + "emergency_contact_phone = '13800000000' WHERE user_id = 1");
        jdbc.update("INSERT INTO address_book (user_id, label, area_text, detail_text, is_default) "
                + "VALUES (1, '家', '梧桐苑小区 7 号楼', '2 单元 603', 1)");

        AssistantChatRequest guideRequest = new AssistantChatRequest();
        guideRequest.setMessage("怎么发布求助呀？");
        AssistantChatResponse guide = assistantService.chat(1L, guideRequest);
        assertEquals("GUIDE", guide.getIntent());
        assertEquals("NONE", guide.getAction());
        assertNotNull(guide.getSessionId());

        AssistantChatRequest publishRequest = new AssistantChatRequest();
        publishRequest.setMessage("我想发布一个求助：帮我去药店买盒降压药，今天需要");
        AssistantChatResponse draft = assistantService.chat(1L, publishRequest);
        assertEquals("PUBLISH", draft.getIntent());
        assertNotNull(draft.getDraft());
        assertEquals("HOME_VISIT", draft.getDraft().getOrderType());
        assertEquals("TODAY", draft.getDraft().getTimeExpect());
        Long addressId = jdbc.queryForObject("SELECT id FROM address_book WHERE user_id = 1", Long.class);
        assertEquals(addressId, draft.getDraft().getAddressId());
        assertEquals("READY_TO_CONFIRM", draft.getAction());
        assertNotNull(draft.getConfirmToken());

        AssistantConfirmRequest confirm = new AssistantConfirmRequest();
        confirm.setSessionId(draft.getSessionId());
        confirm.setConfirmToken(draft.getConfirmToken());
        AssistantChatResponse published = assistantService.confirm(1L, confirm);
        assertEquals("PUBLISHED", published.getAction());
        assertNotNull(published.getOrderId());

        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT status FROM help_order WHERE id = ?", String.class, published.getOrderId()));
        assertEquals("HOME_VISIT", jdbc.queryForObject(
                "SELECT order_type FROM help_order WHERE id = ?", String.class, published.getOrderId()));
    }
}
