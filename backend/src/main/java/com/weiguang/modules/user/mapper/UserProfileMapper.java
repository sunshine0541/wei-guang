package com.weiguang.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.user.entity.UserProfile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户画像 Mapper。
 */
@Mapper
public interface UserProfileMapper extends BaseMapper<UserProfile> {
}
