package com.weiguang.modules.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.chat.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 聊天会话 Mapper。
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}
