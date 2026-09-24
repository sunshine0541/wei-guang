package com.weiguang.modules.security.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.security.entity.SensitiveWord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感词与防诈要素词库 Mapper。
 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWord> {
}
