package com.weiguang.modules.security.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.modules.security.entity.SensitiveWord;
import com.weiguang.modules.security.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词本地规则引擎（详设 §5.3 第 1 层：本地先判，contains 扫描）。
 * 词库来自 sensitive_word（enabled=1），启动时一次性载入。
 * 发布场景只拦 action=BLOCK；聊天场景命中 HIGHLIGHT 高亮、命中 REVIEW 交能力二复核。
 */
@Service
public class SensitiveWordService {

    public static final String ACTION_BLOCK = "BLOCK";
    public static final String ACTION_HIGHLIGHT = "HIGHLIGHT";
    public static final String ACTION_REVIEW = "REVIEW";

    private final SensitiveWordMapper sensitiveWordMapper;
    private volatile List<SensitiveWord> words = List.of();

    public SensitiveWordService(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    public void load() {
        words = sensitiveWordMapper.selectList(
                Wrappers.<SensitiveWord>lambdaQuery().eq(SensitiveWord::getEnabled, 1));
    }

    /** 扫描文本命中的词条（不做拦截判定，由调用方按 action 决定行为） */
    public List<SensitiveWord> scan(String text) {
        List<SensitiveWord> hits = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return hits;
        }
        for (SensitiveWord word : words) {
            if (text.contains(word.getWord())) {
                hits.add(word);
            }
        }
        return hits;
    }

    /** 是否命中指定 action 的词（发布场景用 BLOCK，聊天场景用 HIGHLIGHT/REVIEW） */
    public boolean hit(String text, String action) {
        return scan(text).stream().anyMatch(word -> action.equals(word.getAction()));
    }
}
