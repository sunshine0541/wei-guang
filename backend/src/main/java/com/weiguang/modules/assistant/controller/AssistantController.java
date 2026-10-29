package com.weiguang.modules.assistant.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.assistant.dto.AssistantChatRequest;
import com.weiguang.modules.assistant.dto.AssistantChatResponse;
import com.weiguang.modules.assistant.dto.AssistantConfirmRequest;
import com.weiguang.modules.assistant.dto.AssistantDraftUpdateRequest;
import com.weiguang.modules.assistant.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手：问答 + 发布草稿 + 确认发布（能力五）。
 * 写操作必须经过 confirmToken，且复用 OrderService.publish 的全部校验。
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    /** 对话：问答、补槽、生成确认草稿 */
    @PostMapping("/chat")
    @RateLimit(prefix = "rate:assistant:", limitKey = "rate.publish_per_minute")
    public Result<AssistantChatResponse> chat(@Valid @RequestBody AssistantChatRequest request) {
        return Result.ok(assistantService.chat(AuthContext.requireUid(), request));
    }

    /** 确认发布：confirmToken 一次性使用 */
    @PostMapping("/confirm")
    @RateLimit(prefix = "rate:assistant-confirm:", limitKey = "rate.publish_per_minute")
    public Result<AssistantChatResponse> confirm(@Valid @RequestBody AssistantConfirmRequest request) {
        return Result.ok(assistantService.confirm(AuthContext.requireUid(), request));
    }

    /** 恢复会话：页面重新进入时校验服务端会话是否还在 */
    @GetMapping("/session")
    public Result<AssistantChatResponse> session(@RequestParam String sessionId) {
        return Result.ok(assistantService.restore(AuthContext.requireUid(), sessionId));
    }

    /** 编辑草稿：修改后旧确认令牌作废，重新计算缺失项 */
    @PutMapping("/draft")
    public Result<AssistantChatResponse> updateDraft(@Valid @RequestBody AssistantDraftUpdateRequest request) {
        return Result.ok(assistantService.updateDraft(AuthContext.requireUid(), request));
    }
}
