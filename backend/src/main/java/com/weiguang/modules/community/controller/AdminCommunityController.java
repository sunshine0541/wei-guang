package com.weiguang.modules.community.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.modules.community.dto.AdminOfficialPostRequest;
import com.weiguang.modules.community.dto.AdminPostDetailVO;
import com.weiguang.modules.community.dto.AdminPostReviewRequest;
import com.weiguang.modules.community.dto.AdminPostVO;
import com.weiguang.modules.community.service.AdminCommunityService;
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
 * 社区 2.0 管理端（详设 §15.1.5）。
 */
@RestController
@RequestMapping("/admin/community")
public class AdminCommunityController {

    private final AdminCommunityService adminCommunityService;

    public AdminCommunityController(AdminCommunityService adminCommunityService) {
        this.adminCommunityService = adminCommunityService;
    }

    @GetMapping("/posts")
    public Result<PageVO<AdminPostVO>> list(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Long page,
                                            @RequestParam(required = false) Long pageSize) {
        return Result.ok(adminCommunityService.list(status, category, keyword, page, pageSize));
    }

    @GetMapping("/posts/{id}")
    public Result<AdminPostDetailVO> detail(@PathVariable Long id) {
        return Result.ok(adminCommunityService.detail(id));
    }

    @PostMapping("/posts/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody AdminPostReviewRequest request) {
        adminCommunityService.review(id, request);
        return Result.ok();
    }

    @PostMapping("/posts/{id}/hide")
    public Result<Void> hide(@PathVariable Long id) {
        adminCommunityService.hide(id);
        return Result.ok();
    }

    @PostMapping("/posts/{id}/restore")
    public Result<Void> restore(@PathVariable Long id) {
        adminCommunityService.restore(id);
        return Result.ok();
    }

    @DeleteMapping("/posts/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminCommunityService.delete(id);
        return Result.ok();
    }

    @PostMapping("/official")
    public Result<Long> official(@Valid @RequestBody AdminOfficialPostRequest request) {
        return Result.ok(adminCommunityService.createOfficial(request));
    }

    @PostMapping("/comments/{id}/review")
    public Result<Void> reviewComment(@PathVariable Long id, @Valid @RequestBody AdminPostReviewRequest request) {
        adminCommunityService.reviewComment(id, request);
        return Result.ok();
    }

    @DeleteMapping("/comments/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        adminCommunityService.deleteComment(id);
        return Result.ok();
    }
}
