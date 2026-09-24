package com.weiguang.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号主体 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
