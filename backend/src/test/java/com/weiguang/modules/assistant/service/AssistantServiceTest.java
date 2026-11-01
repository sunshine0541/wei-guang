package com.weiguang.modules.assistant.service;

import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.AssistantResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.assistant.dto.AssistantChatRequest;
import com.weiguang.modules.assistant.dto.AssistantChatResponse;
import com.weiguang.modules.assistant.dto.AssistantConfirmRequest;
import com.weiguang.modules.assistant.dto.AssistantDraftUpdateRequest;
import com.weiguang.modules.assistant.dto.AssistantSession;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.assistant.dto.PublishDraftVO;
import com.weiguang.modules.order.service.OrderService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.dto.AddressVO;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import com.weiguang.modules.user.service.AddressService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 助手槽位逻辑单元测试：只发“帮我发布求助”或先点时间，不能生成发布草稿。
 */
@ExtendWith(MockitoExtension.class)
class AssistantServiceTest {

    @Mock
    private AiService aiService;
    @Mock
    private OrderService orderService;
    @Mock
    private AddressService addressService;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private ConfigService configService;
    @Mock
    private AssistantSessionStore sessionStore;

    @InjectMocks
    private AssistantService assistantService;

    @Test
    void commandOnlyDoesNotCreateDraft() {
        AssistantResult ai = new AssistantResult();
        ai.setIntent("PUBLISH");
        ai.setReply("好的，请告诉我具体需要邻居帮什么忙。");
        ai.setPublishText("");
        when(aiService.assistant("帮我发布求助")).thenReturn(ai);
        when(sessionStore.get(anyString())).thenReturn(null);

        AssistantChatRequest request = new AssistantChatRequest();
        request.setMessage("帮我发布求助");
        AssistantChatResponse response = assistantService.chat(1L, request);

        assertEquals("COLLECTING", response.getAction());
        assertNull(response.getDraft());
        assertNull(response.getConfirmToken());

        ArgumentCaptor<AssistantSession> captor = ArgumentCaptor.forClass(AssistantSession.class);
        verify(sessionStore).put(anyString(), captor.capture());
        AssistantSession stored = captor.getValue();
        assertTrue(stored.isAwaitingDescription());

        AssistantResult next = new AssistantResult();
        next.setIntent("UNKNOWN");
        next.setReply("您好，请问您是想咨询如何发布求助，还是想了解平台的使用指南呢？");
        next.setPublishText("");
        when(aiService.assistant("打球和下棋")).thenReturn(next);
        UnderstandHelpResult understanding = new UnderstandHelpResult();
        understanding.setOrderType("REMOTE");
        understanding.setTitle("打球和下棋");
        understanding.setTags(List.of("其他"));
        understanding.setUrgencySuggest("NORMAL");
        understanding.setConfidence(0.9);
        when(aiService.understandHelp("打球和下棋")).thenReturn(understanding);
        when(sessionStore.get(anyString())).thenReturn(stored);
        when(addressService.list(1L)).thenReturn(List.of());
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(configService.getInt("content.desc_max_length", 200)).thenReturn(200);

        AssistantChatRequest description = new AssistantChatRequest();
        description.setSessionId(response.getSessionId());
        description.setMessage("打球和下棋");
        AssistantChatResponse draft = assistantService.chat(1L, description);

        assertNotNull(draft.getDraft());
        assertEquals("ACCOMPANY", draft.getDraft().getOrderType());
    }

    @Test
    void selectedAddressFillsLabel() {
        AssistantSession session = new AssistantSession();
        session.setSessionId("s1");
        session.setUserId(1L);
        session.setStage("COLLECTING");
        PublishDraftVO draft = new PublishDraftVO();
        draft.setDescription("打球和下棋");
        draft.setTitle("打球和下棋");
        draft.setOrderType("ACCOMPANY");
        draft.setUrgentFlag(0);
        session.setDraft(draft);
        when(sessionStore.get(anyString())).thenReturn(session);

        AddressVO address = new AddressVO();
        address.setId(5L);
        address.setLabel("家");
        address.setAreaText("梧桐苑小区");
        address.setIsDefault(1);
        when(addressService.list(1L)).thenReturn(List.of(address));
        when(userProfileMapper.selectOne(any())).thenReturn(null);
        when(configService.getInt("content.desc_max_length", 200)).thenReturn(200);

        AssistantChatRequest request = new AssistantChatRequest();
        request.setSessionId("s1");
        request.setMessage("就用这个地址，继续发布");
        request.setAddressId(5L);
        request.setTimeExpect("TODAY");
        AssistantChatResponse response = assistantService.chat(1L, request);

        assertNotNull(response.getDraft());
        assertEquals("ACCOMPANY", response.getDraft().getOrderType());
        assertEquals("家（梧桐苑小区）", response.getDraft().getAddressLabel());
        assertEquals("TODAY", response.getDraft().getTimeExpect());
    }

    @Test
    void confirmRejectsLegacyCommandOnlyDraft() {
        AssistantSession session = new AssistantSession();
        session.setSessionId("s1");
        session.setUserId(1L);
        session.setStage("WAITING_CONFIRM");
        session.setConfirmToken("token");
        PublishDraftVO draft = new PublishDraftVO();
        draft.setDescription("帮我发布求助");
        session.setDraft(draft);
        when(sessionStore.get(anyString())).thenReturn(session);
        when(sessionStore.consume(anyString(), anyString())).thenReturn(session);

        AssistantConfirmRequest request = new AssistantConfirmRequest();
        request.setSessionId("s1");
        request.setConfirmToken("token");

        assertThrows(BizException.class, () -> assistantService.confirm(1L, request));
        // 确认令牌已被原子消费，非法草稿不会落库
        verify(sessionStore).consume(anyString(), anyString());
    }

    @Test
    void timeSelectionWithoutDescriptionAsksForDescription() {
        when(sessionStore.get(anyString())).thenReturn(null);

        AssistantChatRequest request = new AssistantChatRequest();
        request.setSessionId("s1");
        request.setMessage("时间选好了，继续发布");
        request.setTimeExpect("THIS_WEEK");
        AssistantChatResponse response = assistantService.chat(1L, request);

        assertEquals("COLLECTING", response.getAction());
        assertNull(response.getDraft());
        assertNull(response.getConfirmToken());
    }

    @Test
    void restoreExpiredReturnsExpired() {
        when(sessionStore.get(anyString())).thenReturn(null);

        AssistantChatResponse response = assistantService.restore(1L, "s1");

        assertEquals("EXPIRED", response.getAction());
    }

    @Test
    void editDraftInvalidatesOldToken() {
        AssistantSession session = completeSession();
        when(sessionStore.get(anyString())).thenReturn(session);
        AddressVO address = address(5L);
        when(addressService.list(1L)).thenReturn(List.of(address));
        UserProfile profile = new UserProfile();
        profile.setEmergencyContactName("家人");
        profile.setEmergencyContactPhone("13800000000");
        when(userProfileMapper.selectOne(any())).thenReturn(profile);
        when(configService.getInt("content.desc_max_length", 200)).thenReturn(200);

        AssistantDraftUpdateRequest request = new AssistantDraftUpdateRequest();
        request.setSessionId("s1");
        request.setTitle("新标题");
        request.setDescription("新描述");
        request.setOrderType("ACCOMPANY");
        request.setTimeExpect("TODAY");
        request.setAddressId(5L);
        request.setUrgentFlag(0);

        AssistantChatResponse response = assistantService.updateDraft(1L, request);

        assertEquals("READY_TO_CONFIRM", response.getAction());
        assertEquals("新标题", response.getDraft().getTitle());
        assertEquals("新描述", response.getDraft().getDescription());
        assertNotEquals("old-token", response.getConfirmToken());
        assertEquals("家（梧桐苑小区）", response.getDraft().getAddressLabel());
    }

    @Test
    void editToRemoteClearsAddress() {
        AssistantSession session = completeSession();
        when(sessionStore.get(anyString())).thenReturn(session);
        when(addressService.list(1L)).thenReturn(List.of());
        when(configService.getInt("content.desc_max_length", 200)).thenReturn(200);

        AssistantDraftUpdateRequest request = new AssistantDraftUpdateRequest();
        request.setSessionId("s1");
        request.setTitle("旧标题");
        request.setDescription("旧描述");
        request.setOrderType("REMOTE");
        request.setTimeExpect("TODAY");
        request.setAddressId(5L);
        request.setUrgentFlag(0);

        AssistantChatResponse response = assistantService.updateDraft(1L, request);

        assertEquals("READY_TO_CONFIRM", response.getAction());
        assertEquals("REMOTE", response.getDraft().getOrderType());
        assertNull(response.getDraft().getAddressId());
        assertNull(response.getDraft().getAddressLabel());
    }

    private AssistantSession completeSession() {
        AssistantSession session = new AssistantSession();
        session.setSessionId("s1");
        session.setUserId(1L);
        session.setStage("WAITING_CONFIRM");
        session.setConfirmToken("old-token");
        PublishDraftVO draft = new PublishDraftVO();
        draft.setTitle("旧标题");
        draft.setDescription("旧描述");
        draft.setOrderType("ACCOMPANY");
        draft.setTimeExpect("TODAY");
        draft.setAddressId(5L);
        draft.setAddressLabel("家");
        draft.setUrgentFlag(0);
        session.setDraft(draft);
        return session;
    }

    private AddressVO address(Long id) {
        AddressVO address = new AddressVO();
        address.setId(id);
        address.setLabel("家");
        address.setAreaText("梧桐苑小区");
        address.setIsDefault(1);
        return address;
    }
}
