package com.weiguang.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;

import java.util.List;

/**
 * JSON 工具（画像的 hobbies / help_directions 等以 JSON 数组存 VARCHAR）。
 */
public final class JsonUtils {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private JsonUtils() {
    }

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR);
        }
    }

    /** 解析字符串数组；null/空/非法一律返回空列表（不因历史脏数据阻断读取） */
    public static List<String> toStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> values = MAPPER.readValue(json, STRING_LIST);
            return values == null ? List.of() : values;
        } catch (Exception e) {
            return List.of();
        }
    }
}
