package com.weiguang.modules.template.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.weiguang.common.util.JsonUtils;
import com.weiguang.infra.cache.HotCacheService;
import com.weiguang.modules.template.dto.TemplateVO;
import com.weiguang.modules.template.entity.HelpTemplate;
import com.weiguang.modules.template.mapper.HelpTemplateMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 求助模板（详设 §3.6 T1）：enabled=1 按 sort 升序，走热点缓存（TTL cache.hot_ttl_seconds，Redis 降级本地）。
 */
@Service
public class HelpTemplateService {

    private static final String CACHE_KEY = "hot:help_template";
    private static final TypeReference<List<TemplateVO>> LIST_TYPE = new TypeReference<>() {
    };

    private final HelpTemplateMapper helpTemplateMapper;
    private final HotCacheService hotCacheService;

    public HelpTemplateService(HelpTemplateMapper helpTemplateMapper, HotCacheService hotCacheService) {
        this.helpTemplateMapper = helpTemplateMapper;
        this.hotCacheService = hotCacheService;
    }

    public List<TemplateVO> list() {
        String cached = hotCacheService.get(CACHE_KEY);
        if (cached != null) {
            return JsonUtils.fromJson(cached, LIST_TYPE);
        }
        List<TemplateVO> templates = helpTemplateMapper.selectList(Wrappers.<HelpTemplate>lambdaQuery()
                        .eq(HelpTemplate::getEnabled, 1)
                        .orderByAsc(HelpTemplate::getSort))
                .stream()
                .map(TemplateVO::from)
                .toList();
        hotCacheService.put(CACHE_KEY, JsonUtils.toJson(templates));
        return templates;
    }
}
