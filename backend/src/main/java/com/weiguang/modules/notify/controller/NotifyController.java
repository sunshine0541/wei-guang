package com.weiguang.modules.notify.controller;

import com.weiguang.common.result.PageVO;
import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.notify.dto.BannerVO;
import com.weiguang.modules.notify.dto.NotificationVO;
import com.weiguang.modules.notify.dto.ReadAllVO;
import com.weiguang.modules.notify.service.NotifyService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 通知中心接口（详设 §3.6 N1–N4）。
 */
@RestController
@RequestMapping("/api")
public class NotifyController {

    private final NotifyService notifyService;

    public NotifyController(NotifyService notifyService) {
        this.notifyService = notifyService;
    }

    /** N1 通知列表（分页，按时间倒序） */
    @GetMapping("/notifications")
    public Result<PageVO<NotificationVO>> list(@RequestParam(required = false) Long page,
                                               @RequestParam(required = false) Long pageSize) {
        return Result.ok(notifyService.list(AuthContext.requireUid(), page, pageSize));
    }

    /** N2 首页横幅（最近 1 条未读+聚合） */
    @GetMapping("/notifications/banner")
    public Result<BannerVO> banner() {
        return Result.ok(notifyService.banner(AuthContext.requireUid()));
    }

    /** N3 已读（幂等） */
    @PostMapping("/notifications/{id}/read")
    public Result<Void> read(@PathVariable Long id) {
        notifyService.read(AuthContext.requireUid(), id);
        return Result.ok();
    }

    /** N4 全部已读（幂等，返回本次清零条数） */
    @PostMapping("/notifications/read-all")
    public Result<ReadAllVO> readAll() {
        return Result.ok(notifyService.readAll(AuthContext.requireUid()));
    }

    /** B：删除本人通知 */
    @DeleteMapping("/notifications/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        notifyService.remove(AuthContext.requireUid(), id);
        return Result.ok();
    }
}