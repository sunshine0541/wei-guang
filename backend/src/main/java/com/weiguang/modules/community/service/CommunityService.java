package com.weiguang.modules.community.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.community.dto.CommunityFeedVO;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 社区页：邻里动态只读流。
 * 取最近完成的互助单，只暴露标题/类型/标签/双方昵称与完成时间，不下发地址、门牌和描述。
 */
@Service
public class CommunityService {

    private final HelpOrderMapper helpOrderMapper;
    private final UserProfileMapper userProfileMapper;

    public CommunityService(HelpOrderMapper helpOrderMapper, UserProfileMapper userProfileMapper) {
        this.helpOrderMapper = helpOrderMapper;
        this.userProfileMapper = userProfileMapper;
    }

    public PageVO<CommunityFeedVO> feed(Long page, Long pageSize) {
        Page<HelpOrder> query = new Page<>(
                page == null || page < 1 ? 1 : page,
                PageUtils.normalizeSize(pageSize));
        Page<HelpOrder> result = helpOrderMapper.selectPage(query, Wrappers.<HelpOrder>lambdaQuery()
                .eq(HelpOrder::getStatus, OrderStatus.COMPLETED.name())
                .isNotNull(HelpOrder::getCompletedAt)
                .isNotNull(HelpOrder::getHelperId)
                .orderByDesc(HelpOrder::getCompletedAt)
                .orderByDesc(HelpOrder::getId));

        Map<Long, UserProfile> profiles = loadProfiles(result.getRecords());
        return PageVO.of(result.convert(order -> toVO(order, profiles)));
    }

    private Map<Long, UserProfile> loadProfiles(List<HelpOrder> orders) {
        Set<Long> userIds = new HashSet<>();
        for (HelpOrder order : orders) {
            if (order.getHelperId() != null) {
                userIds.add(order.getHelperId());
            }
            if (order.getRequesterId() != null) {
                userIds.add(order.getRequesterId());
            }
        }
        Map<Long, UserProfile> profiles = new HashMap<>();
        if (userIds.isEmpty()) {
            return profiles;
        }
        List<UserProfile> list = userProfileMapper.selectList(
                Wrappers.<UserProfile>lambdaQuery().in(UserProfile::getUserId, userIds));
        for (UserProfile profile : list) {
            profiles.put(profile.getUserId(), profile);
        }
        return profiles;
    }

    private CommunityFeedVO toVO(HelpOrder order, Map<Long, UserProfile> profiles) {
        CommunityFeedVO vo = new CommunityFeedVO();
        vo.setOrderId(order.getId());
        vo.setTitle(order.getTitle());
        vo.setOrderType(order.getOrderType());
        vo.setTags(JsonUtils.toStringList(order.getTags()));
        vo.setHelperId(order.getHelperId());
        UserProfile helper = profiles.get(order.getHelperId());
        vo.setHelperNickname(helper == null ? null : helper.getNickname());
        vo.setHelperAvatarUrl(helper == null ? null : helper.getAvatarUrl());
        UserProfile requester = profiles.get(order.getRequesterId());
        vo.setRequesterNickname(requester == null ? null : requester.getNickname());
        vo.setCompletedAt(order.getCompletedAt());
        return vo;
    }
}
