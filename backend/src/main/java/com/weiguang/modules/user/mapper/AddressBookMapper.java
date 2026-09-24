package com.weiguang.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.user.entity.AddressBook;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 常去地址簿 Mapper。
 */
@Mapper
public interface AddressBookMapper extends BaseMapper<AddressBook> {

    /** U6 注销：地址簿物理删除（详设 §3.3 U6 要求物理删除，绕过逻辑删除） */
    @Delete("DELETE FROM address_book WHERE user_id = #{userId}")
    int physicalDeleteByUserId(@Param("userId") Long userId);
}
