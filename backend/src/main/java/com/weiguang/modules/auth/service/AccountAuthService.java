package com.weiguang.modules.auth.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.auth.dto.LoginRequest;
import com.weiguang.modules.auth.dto.RegisterRequest;
import com.weiguang.modules.auth.dto.WxLoginVO;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

/**
 * 账号密码注册/登录（D31，需求方拍板的登录方式变更）：
 * 出参与 A1 同构（AuthVoAssembler），复用限流、JWT 与建号链路；密码 BCrypt 存储。
 * 注册页勾选隐私说明即视为 A3 同意（D31 隐私入口变更，P01 保留为老用户/兜底页）。
 */
@Slf4j
@Service
public class AccountAuthService {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 32;

    private final UserMapper userMapper;
    private final UserRegistrar userRegistrar;
    private final AuthVoAssembler authVoAssembler;
    private final ConfigService configService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AccountAuthService(UserMapper userMapper,
                              UserRegistrar userRegistrar,
                              AuthVoAssembler authVoAssembler,
                              ConfigService configService) {
        this.userMapper = userMapper;
        this.userRegistrar = userRegistrar;
        this.authVoAssembler = authVoAssembler;
        this.configService = configService;
    }

    /** 注册：账号名 4-20 位字母数字下划线、密码 6-32 位、必须勾选隐私说明；重复账号名温柔拒绝 */
    @Transactional
    public WxLoginVO register(RegisterRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();
        if (request.getPrivacyAgreed() == null || !request.getPrivacyAgreed()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请先阅读并勾选同意《隐私说明》");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "账号名用 4-20 位字母或数字吧");
        }
        if (password.length() < PASSWORD_MIN || password.length() > PASSWORD_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID, "密码要用 6-32 位哦");
        }
        Long exists = userMapper.selectCount(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.PARAM_INVALID, "这个账号名已经有人用啦，换一个试试");
        }
        User user;
        try {
            user = userRegistrar.register(null, username, passwordEncoder.encode(password));
        } catch (DuplicateKeyException e) {
            // 并发注册撞唯一键
            throw new BizException(ErrorCode.PARAM_INVALID, "这个账号名已经有人用啦，换一个试试");
        }
        // 注册页勾选即落 A3 同意记录（隐私入口变更，D31）
        String version = configService.get("privacy.policy_version", "v1.0");
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPrivacyVersion, version)
                .set(User::getPrivacyAgreedAt, LocalDateTime.now()));
        user.setPrivacyVersion(version);
        log.info("账号注册成功 uid={} username={}（隐私已同意 {}）", user.getId(), username, version);
        return authVoAssembler.assemble(user, true);
    }

    /** 登录：账号或密码错误统一 20001 + 温柔文案（不区分账号是否存在） */
    public WxLoginVO login(LoginRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        String password = request.getPassword() == null ? "" : request.getPassword();
        User user = userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码不对，再试一次");
        }
        return authVoAssembler.assemble(user, false);
    }
}