package com.weiguang.modules.community.controller;

import com.weiguang.common.annotation.RateLimit;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.community.dto.CommentCreateRequest;
import com.weiguang.modules.community.dto.CommentVO;
import com.weiguang.modules.community.dto.CommunitySummaryVO;
import com.weiguang.modules.community.dto.PostCreateRequest;
import com.weiguang.modules.community.dto.PostCreateVO;
import com.weiguang.modules.community.dto.PostVO;
import com.weiguang.modules.community.dto.ReportCreateRequest;
import com.weiguang.modules.community.service.CommunityService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区 2.0 用户端（详设 §15.1.4 CM1–CM12）。
 */
@RestController
@RequestMapping("/api/community")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /** CM1 顶部三块聚合 */
    @GetMapping("/summary")
    public Result<CommunitySummaryVO> summary() {
        return Result.ok(communityService.summary());
    }

    /** CM2 动态列表（tab=NEIGHBOR/STORY/OFFICIAL） */
    @GetMapping("/posts")
    public Result<PageVO<PostVO>> list(@RequestParam(required = false) String tab,
                                       @RequestParam(required = false) Long page,
                                       @RequestParam(required = false) Long pageSize) {
        return Result.ok(communityService.list(AuthContext.requireUid(), tab, page, pageSize));
    }

    /** CM3 发布动态 */
    @PostMapping("/posts")
    @RateLimit(prefix = "rate:post:", limitKey = "rate.publish_per_minute")
    public Result<PostCreateVO> create(@Valid @RequestBody PostCreateRequest request) {
        return Result.ok(communityService.create(AuthContext.requireUid(), request));
    }

    /** CM6 我的动态（含审核中/未通过） */
    @GetMapping("/posts/mine")
    public Result<PageVO<PostVO>> mine(@RequestParam(required = false) Long page,
                                       @RequestParam(required = false) Long pageSize) {
        return Result.ok(communityService.mine(AuthContext.requireUid(), page, pageSize));
    }

    /** CM4 动态详情 */
    @GetMapping("/posts/{id}")
    public Result<PostVO> detail(@PathVariable Long id) {
        return Result.ok(communityService.detail(AuthContext.requireUid(), id));
    }

    /** CM5 删除动态（作者） */
    @DeleteMapping("/posts/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        communityService.delete(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** CM7 点赞 */
    @PostMapping("/posts/{id}/like")
    public Result<Void> like(@PathVariable Long id) {
        communityService.like(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** CM8 取消点赞 */
    @DeleteMapping("/posts/{id}/like")
    public Result<Void> unlike(@PathVariable Long id) {
        communityService.unlike(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** CM9 评论列表 */
    @GetMapping("/posts/{id}/comments")
    public Result<PageVO<CommentVO>> comments(@PathVariable Long id,
                                              @RequestParam(required = false) Long page,
                                              @RequestParam(required = false) Long pageSize) {
        return Result.ok(communityService.comments(AuthContext.requireUid(), id, page, pageSize));
    }

    /** CM10 发表评论 */
    @PostMapping("/posts/{id}/comments")
    @RateLimit(prefix = "rate:comment:", limitKey = "rate.chat_per_minute")
    public Result<Void> comment(@PathVariable Long id, @Valid @RequestBody CommentCreateRequest request) {
        communityService.createComment(AuthContext.requireUid(), id, request);
        return Result.ok();
    }

    /** CM11 删除评论（作者） */
    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        communityService.deleteComment(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** CM12 举报动态/评论 */
    @PostMapping("/reports")
    @RateLimit(prefix = "rate:report:", limitKey = "rate.publish_per_minute")
    public Result<Void> report(@Valid @RequestBody ReportCreateRequest request) {
        communityService.report(AuthContext.requireUid(), request);
        return Result.ok();
    }
}
