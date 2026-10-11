package com.weiguang.modules.light.service;

import com.weiguang.modules.light.entity.LightEvent;
import com.weiguang.modules.light.mapper.LightEventMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 光语事件记录器（表 light_event，只增不改；状态与徽记分开记录，详设 §2.16）。
 */
@Component
public class LightEventRecorder {

    public static final String TYPE_STATE_CHANGE = "STATE_CHANGE";
    public static final String TYPE_BADGE_GAIN = "BADGE_GAIN";
    public static final String BADGE_LIGHT_PASSED = "LIGHT_PASSED";

    private final LightEventMapper lightEventMapper;

    public LightEventRecorder(LightEventMapper lightEventMapper) {
        this.lightEventMapper = lightEventMapper;
    }

    /** 记录一条光语事件（状态变更：from/to 非空；徽记获得：badge 非空） */
    public void record(String eventType, Long uid, String fromState, String toState, String badge, Long orderId) {
        LightEvent event = new LightEvent();
        event.setUserId(uid);
        event.setEventType(eventType);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setBadge(badge);
        event.setOrderId(orderId);
        event.setCreatedAt(LocalDateTime.now());
        lightEventMapper.insert(event);
    }
}