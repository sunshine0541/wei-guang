package com.weiguang.modules.chat.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.chat.dto.MessageVO;
import com.weiguang.modules.chat.dto.ReadVO;
import com.weiguang.modules.chat.dto.SendMessageRequest;
import com.weiguang.modules.chat.dto.SessionVO;
import com.weiguang.modules.chat.service.ChatService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聊天接口（详设 §3.3 C1–C5）。
 */
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** C1 当前轮会话（含接单者专属地址/紧急联系人，见《实现偏差清单》D10） */
    @GetMapping("/api/orders/{orderId}/session")
    public Result<SessionVO> session(@PathVariable Long orderId) {
        return Result.ok(chatService.getSession(AuthContext.requireUid(), orderId));
    }

    /** C2 消息历史（分页，倒序） */
    @GetMapping("/api/sessions/{id}/messages")
    public Result<PageVO<MessageVO>> messages(@PathVariable Long id,
                                              @RequestParam(required = false) Long page,
                                              @RequestParam(required = false) Long pageSize) {
        return Result.ok(chatService.messages(AuthContext.requireUid(), id, page, pageSize));
    }

    /** C3 发消息（REST 兜底通道；限流 20 次/分） */
    @PostMapping("/api/sessions/{id}/messages")
    @RateLimit(prefix = "rate:chat:", limitKey = "rate.chat_per_minute")
    public Result<MessageVO> send(@PathVariable Long id, @RequestBody SendMessageRequest request) {
        return Result.ok(chatService.send(AuthContext.requireUid(), id, request));
    }

    /** C4 会话已读（未读数清零，幂等） */
    @PostMapping("/api/sessions/{id}/read")
    public Result<ReadVO> read(@PathVariable Long id) {
        return Result.ok(chatService.read(AuthContext.requireUid(), id));
    }

    /** C5 快捷短语（按角色 HELPER/REQUESTER 返回两套，默认求助者） */
    @GetMapping("/api/quick-phrases")
    public Result<List<String>> quickPhrases(@RequestParam(required = false) String role) {
        return Result.ok(chatService.quickPhrases(role));
    }
}