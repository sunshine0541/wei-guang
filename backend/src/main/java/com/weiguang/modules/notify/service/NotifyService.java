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
    /** 详设 §4.4 NEW_MESSAGE */
    public static final String TYPE_NEW_MESSAGE = "NEW_MESSAGE";

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

    /** 新消息：{name} 给您发来新消息，去看看吧（jump=SESSION，离线方落库） */
    public void newMessage(Long peerId, String senderNickname, Long sessionId) {
        Notification notification = new Notification();
        notification.setUserId(peerId);
        notification.setType(TYPE_NEW_MESSAGE);
        notification.setTitle("新消息");
        notification.setContent(senderNickname + " 给您发来新消息，去看看吧");
        notification.setJumpType("SESSION");
        notification.setJumpId(sessionId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }

    /** 对方标记了完成：{name} 说"{title}"已经完成啦，请您确认一下（详设 §4.4 CONFIRM_REQUEST） */
    public void confirmRequest(Long peerId, String initiatorNickname, String orderTitle, Long orderId) {
        insert(peerId, "CONFIRM_REQUEST", "对方标记了完成",
                initiatorNickname + " 说\"" + orderTitle + "\"已经完成啦，请您确认一下", "ORDER", orderId);
    }

    /** 这次帮助完成了："{title}"已圆满完成，感谢您的信任（详设 §4.4 SETTLED） */
    public void settled(Long userId, String orderTitle, Long orderId) {
        insert(userId, "SETTLED", "这次帮助完成了",
                "\"" + orderTitle + "\"已圆满完成，感谢您的信任", "ORDER", orderId);
    }

    /** 积分到账：您获得了 {points} 积分，感谢您的善意（数值取 points.per_complete） */
    public void pointsCredited(Long userId, int points) {
        insert(userId, "POINTS_CREDITED", "积分到账",
                "您获得了 " + points + " 积分，感谢您的善意", "NONE", null);
    }

    /** 微光值变动：您的微光值有变动，去看看明细吧（详设 §4.4 CREDIT_CHANGED） */
    public void creditChanged(Long userId) {
        insert(userId, "CREDIT_CHANGED", "微光值变动", "您的微光值有变动，去看看明细吧", "NONE", null);
    }

    private void insert(Long userId, String type, String title, String content, String jumpType, Long jumpId) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setJumpType(jumpType);
        notification.setJumpId(jumpId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);
    }
}
