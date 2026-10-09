package com.weiguang.modules.notify.service;

import com.weiguang.modules.notify.entity.Notification;
import com.weiguang.modules.notify.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

/**
 * 通知服务（详设 §4.4 文案模板，写死不得改写）。
 * T07 先实现直接落库（与状态变更同事务）；T12 补横幅/红点/列表与事务外推送。
 */
@Service
public class NotifyService {

    /** 详设 §4.4 ACCEPTED */
    public static final String TYPE_ACCEPTED = "ACCEPTED";

    private final NotificationMapper notificationMapper;

    public NotifyService(NotificationMapper notificationMapper) {
        this.notificationMapper = notificationMapper;
    }

    /** 有人来接您的求助啦：{name} 愿意帮您"{title}"，去打个招呼吧 */
    public void accepted(Long requesterId, String helperNickname, String orderTitle, Long orderId) {
        Notification notification = new Notification();
        notification.setUserId(requesterId);
        notification.setType(TYPE_ACCEPTED);
        notification.setTitle("有人来接您的求助啦");
        notification.setContent(helperNickname + " 愿意帮您\"" + orderTitle + "\"，去打个招呼吧");
        notification.setJumpType("ORDER");
        notification.setJumpId(orderId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }
}
