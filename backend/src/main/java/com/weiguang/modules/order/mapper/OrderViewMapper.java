package com.weiguang.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.weiguang.modules.order.entity.OrderView;
import org.apache.ibatis.annotations.Mapper;

/**
 * 浏览去重记录 Mapper。
 */
@Mapper
public interface OrderViewMapper extends BaseMapper<OrderView> {
}
