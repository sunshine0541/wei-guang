package com.weiguang.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.admin.dto.AdminUserDetailVO;
import com.weiguang.modules.admin.dto.AdminUserVO;
import com.weiguang.modules.admin.dto.CreditAdjustRequest;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.incentive.service.IncentiveService;
import com.weiguang.modules.notify.service.NotifyService;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 管理端用户管理（详设 §3.4 / §6.3）：列表/详情（画像+账本明细+订单摘要）/封禁/解封/微光值人工处置。
 */
@Slf4j
@Service
public class AdminUserService {

    private static final int REMARK_MAX = 200;
    private static final long LEDGER_PREVIEW_SIZE = 10L;

    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final HelpOrderMapper helpOrderMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final IncentiveService incentiveService;
    private final NotifyService notifyService;

    public AdminUserService(UserMapper userMapper,
                            UserProfileMapper userProfileMapper,
                            HelpOrderMapper helpOrderMapper,
                            CreditLedgerMapper creditLedgerMapper,
                            IncentiveService incentiveService,
                            NotifyService notifyService) {
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.helpOrderMapper = helpOrderMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.incentiveService = incentiveService;
        this.notifyService = notifyService;
    }

    /** GET /admin/users 列表（关键词=昵称或 openid 模糊；状态筛选；分页） */
    public PageVO<AdminUserVO> list(String keyword, String status, Long page, Long pageSize) {
        LambdaQueryWrapper<User> wrapper = Wrappers.<User>lambdaQuery()
                .eq(status != null && !status.isBlank(), User::getStatus, status)
                .orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            List<Long> matchedIds = userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                            .like(UserProfile::getNickname, keyword)).stream()
                    .map(UserProfile::getUserId).toList();
            wrapper.and(w -> {
                w.like(User::getOpenid, keyword);
                if (!matchedIds.isEmpty()) {
                    w.or().in(User::getId, matchedIds);
                }
            });
        }
        Page<User> result = userMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)), wrapper);
        Map<Long, UserProfile> profiles = profiles(result.getRecords().stream().map(User::getId).toList());
        return PageVO.of(result.convert(user -> toVO(user, profiles.get(user.getId()))));
    }

    /** GET /admin/users/{id} 详情（画像+微光值/积分明细+订单摘要） */
    public AdminUserDetailVO detail(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, id));
        AdminUserDetailVO vo = new AdminUserDetailVO();
        vo.setId(user.getId());
        vo.setOpenid(user.getOpenid());
        vo.setUiMode(user.getUiMode());
        vo.setMotionEnabled(user.getMotionEnabled());
        vo.setCreditScore(user.getCreditScore());
        vo.setViolationCount(user.getViolationCount());
        vo.setStatus(user.getStatus());
        vo.setLightState(user.getLightState());
        vo.setLightPassed(user.getLightPassed());
        vo.setHelpedBefore(user.getHelpedBefore());
        vo.setCreatedAt(user.getCreatedAt());
        if (profile != null) {
            vo.setNickname(profile.getNickname());
            vo.setAvatarUrl(profile.getAvatarUrl());
            vo.setAge(profile.getAge());
            vo.setSignature(profile.getSignature());
            vo.setHobbies(JsonUtils.toStringList(profile.getHobbies()));
            vo.setCareTip(profile.getCareTip());
            vo.setEmergencyContactName(profile.getEmergencyContactName());
            vo.setEmergencyContactPhone(profile.getEmergencyContactPhone());
            vo.setPhone(profile.getPhone());
        }
        vo.setCredit(incentiveService.credit(id, 1L, LEDGER_PREVIEW_SIZE));
        vo.setPoints(incentiveService.points(id, 1L, LEDGER_PREVIEW_SIZE));
        vo.setPublishedCount(helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, id)));
        vo.setCompletedCount(helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getRequesterId, id)
                .eq(HelpOrder::getStatus, "COMPLETED")));
        vo.setHelpedCompletedCount(helpOrderMapper.selectCount(Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getHelperId, id)
                .eq(HelpOrder::getStatus, "COMPLETED")));
        return vo;
    }

    /** POST /admin/users/{id}/ban 封禁（注销账号不可封禁） */
    @Transactional
    public void ban(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .ne(User::getStatus, "CANCELLED")
                .set(User::getStatus, "BANNED"));
        log.info("封禁用户：uid={}", id);
    }

    /** POST /admin/users/{id}/unban 解封（仅封禁中可解封） */
    @Transactional
    public void unban(Long id) {
        int rows = userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .eq(User::getStatus, "BANNED")
                .set(User::getStatus, "NORMAL"));
        if (rows == 0) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        log.info("解封用户：uid={}", id);
    }

    /** POST /admin/users/{id}/credit-adjust 微光值人工处置（ADMIN_ADJUST + 明细 + 通知） */
    @Transactional
    public void creditAdjust(Long adminId, Long id, CreditAdjustRequest request) {
        Integer delta = request == null ? null : request.getDelta();
        String remark = request == null ? null : request.getRemark();
        if (delta == null || delta == 0) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (remark == null || remark.isBlank() || remark.length() > REMARK_MAX) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        int current = user.getCreditScore() == null ? 0 : user.getCreditScore();
        int balance = Math.max(0, current + delta);
        userMapper.update(null, Wrappers.<User>lambdaUpdate()
                .eq(User::getId, id)
                .set(User::getCreditScore, balance));
        CreditLedger ledger = new CreditLedger();
        ledger.setUserId(id);
        ledger.setDelta(delta);
        ledger.setReasonCode("ADMIN_ADJUST");
        ledger.setBalanceAfter(balance);
        ledger.setOperatorId(adminId);
        ledger.setRemark(remark);
        ledger.setCreatedAt(LocalDateTime.now());
        creditLedgerMapper.insert(ledger);
        notifyService.creditChanged(id);
        log.info("微光值人工处置：uid={} delta={} balance={} admin={}", id, delta, balance, adminId);
    }

    private AdminUserVO toVO(User user, UserProfile profile) {
        AdminUserVO vo = new AdminUserVO();
        vo.setId(user.getId());
        vo.setOpenid(user.getOpenid());
        vo.setNickname(profile == null ? null : profile.getNickname());
        vo.setAvatarUrl(profile == null ? null : profile.getAvatarUrl());
        vo.setAge(profile == null ? null : profile.getAge());
        vo.setUiMode(user.getUiMode());
        vo.setStatus(user.getStatus());
        vo.setCreditScore(user.getCreditScore());
        vo.setLightState(user.getLightState());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    private Map<Long, UserProfile> profiles(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> distinct = Set.copyOf(userIds);
        return userProfileMapper.selectList(Wrappers.<UserProfile>lambdaQuery()
                        .in(UserProfile::getUserId, distinct)).stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a));
    }
}