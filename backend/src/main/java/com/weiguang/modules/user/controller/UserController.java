package com.weiguang.modules.user.controller;

import com.weiguang.common.result.Result;
import com.weiguang.infra.interceptor.AuthContext;
import com.weiguang.modules.user.dto.LightInviteRequest;
import com.weiguang.modules.user.dto.ModeRequest;
import com.weiguang.modules.user.dto.ModeVO;
import com.weiguang.modules.user.dto.MotionRequest;
import com.weiguang.modules.user.dto.MotionVO;
import com.weiguang.modules.user.dto.PrivacyAgreeRequest;
import com.weiguang.modules.user.dto.ProfileUpdateRequest;
import com.weiguang.modules.user.dto.ProfileUpdateVO;
import com.weiguang.modules.user.dto.UserProfileVO;
import com.weiguang.modules.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户域接口（详设 §3.2 U1–U6 + A3）。
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** U1 我的档案 */
    @GetMapping("/profile")
    public Result<UserProfileVO> profile() {
        return Result.ok(userService.getProfile(AuthContext.requireUid()));
    }

    /** U2 更新画像 */
    @PutMapping("/profile")
    public Result<ProfileUpdateVO> updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return Result.ok(userService.updateProfile(AuthContext.requireUid(), request));
    }

    /** U3 切换模式（同时重置动效默认值） */
    @PutMapping("/mode")
    public Result<ModeVO> mode(@Valid @RequestBody ModeRequest request) {
        return Result.ok(userService.switchMode(AuthContext.requireUid(), request.getUiMode()));
    }

    /** U4 动效开关 */
    @PutMapping("/motion")
    public Result<MotionVO> motion(@Valid @RequestBody MotionRequest request) {
        return Result.ok(userService.switchMotion(AuthContext.requireUid(), request.getEnabled()));
    }

    /** U5 微光邀请应答 */
    @PostMapping("/light-invite")
    public Result<Void> lightInvite(@Valid @RequestBody LightInviteRequest request) {
        userService.lightInvite(AuthContext.requireUid(), request.getChoice());
        return Result.ok();
    }

    /** U6 注销账号 */
    @PostMapping("/cancel-account")
    public Result<Void> cancelAccount() {
        userService.cancelAccount(AuthContext.requireUid());
        return Result.ok();
    }

    /** A3 隐私同意 */
    @PostMapping("/privacy-agree")
    public Result<Void> privacyAgree(@Valid @RequestBody PrivacyAgreeRequest request) {
        userService.privacyAgree(AuthContext.requireUid(), request.getVersion());
        return Result.ok();
    }
}
