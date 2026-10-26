package com.weiguang.modules.community.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.modules.community.dto.CommunityFeedVO;
import com.weiguang.modules.community.service.CommunityService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区页接口：邻里动态只读流。
 */
@RestController
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @GetMapping("/api/community/feed")
    public Result<PageVO<CommunityFeedVO>> feed(@RequestParam(required = false) Long page,
                                               @RequestParam(required = false) Long pageSize) {
        return Result.ok(communityService.feed(page, pageSize));
    }
}
