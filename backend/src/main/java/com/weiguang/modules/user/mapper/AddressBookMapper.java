package com.weiguang.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.user.entity.AddressBook;
import org.apache.ibatis.annotations.Mapper;

/**
 * 常去地址簿 Mapper。
 */
@Mapper
public interface AddressBookMapper extends BaseMapper<AddressBook> {
}
