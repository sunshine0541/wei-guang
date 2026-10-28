package com.weiguang.modules.notify.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.infra.websocket.WsEnvelope;
import com.weiguang.infra.websocket.WsSessionRegistry;
import com.weiguang.modules.notify.dto.BannerVO;
import com.weiguang.modules.notify.dto.LatestVO;
import com.weiguang.modules.notify.dto.NotificationVO;
import com.weiguang.modules.notify.dto.ReadAllVO;
import com.weiguang.modules.notify.entity.Notification;
import com.weiguang.modules.notify.mapper.NotificationMapper;
import com.weiguang.modules.system.service.ConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 通知服务（详设 §4.4 文案模板，写死不得改写）。
 * T12 定稿：业务只调用本类文案方法——落库与状态变更同事务；在线推送在事务提交后异步发出
 * （§2.18：MVP=notification 落库+在线时 WebSocket 推送；离线自然丢弃，由 P03 横幅与 P16 列表呈现）。
 */
@Service
public class NotifyService {

    /** 详设 §4.4 ACCEPTED */
    public static final String TYPE_ACCEPTED = "ACCEPTED";
    /** 详设 §4.4 NEW_MESSAGE */
    public static final String TYPE_NEW_MESSAGE = "NEW_MESSAGE";
    /** N2 横幅聚合条数上限（sys_config） */
    private static final String BANNER_MAX_EXTRA_KEY = "notify.banner_max_extra";

    private final NotificationMapper notificationMapper;
    private final WsSessionRegistry wsSessionRegistry;
    private final ConfigService configService;

    public NotifyService(NotificationMapper notificationMapper, WsSessionRegistry wsSessionRegistry,
                         ConfigService configService) {
        this.notificationMapper = notificationMapper;
        this.wsSessionRegistry = wsSessionRegistry;
        this.configService = configService;
    }

    // ---------- 文案方法（详设 §4.4，写死不得改写） ----------

    /** 有人来接您的求助啦：{name} 愿意帮您"{title}"，去打个招呼吧 */
    public void accepted(Long requesterId, String helperNickname, String orderTitle, Long orderId) {
        insert(requesterId, TYPE_ACCEPTED, "有人来接您的求助啦",
                helperNickname + " 愿意帮您\"" + orderTitle + "\"，去打个招呼吧", "ORDER", orderId);
    }

    /** 新消息：同一会话只保留一条未读聚合通知，每来一条把 message_count +1（B）
     *  jump=SESSION，jump_id 存 orderId 以直达聊天页（见偏差 D21）；进入聊天室后由 C4 置已读 */
    public void newMessage(Long peerId, String senderNickname, Long orderId) {
        Notification existing = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, peerId)
                .eq(Notification::getType, TYPE_NEW_MESSAGE)
                .eq(Notification::getJumpId, orderId)
                .eq(Notification::getIsRead, 0)
                .orderByDesc(Notification::getId)
                .last("LIMIT 1"));
        String content = senderNickname + " 给您发来新消息，去看看吧";
        if (existing != null) {
            notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                    .eq(Notification::getId, existing.getId())
                    .setSql("message_count = message_count + 1")
                    .set(Notification::getSenderNickname, senderNickname)
                    .set(Notification::getContent, content)
                    .set(Notification::getCreatedAt, LocalDateTime.now()));
            return;
        }
        insert(peerId, TYPE_NEW_MESSAGE, "新消息", content, "SESSION", orderId, 1, senderNickname);
    }

    /** B：进入聊天室时把该会话的聊天通知置为已读（避免"消息已读、通知还挂着"） */
    public void readChatNotifications(Long uid, Long orderId) {
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getType, TYPE_NEW_MESSAGE)
                .eq(Notification::getJumpId, orderId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
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

    /** 荣誉积分到账：您获得了 {points} 荣誉积分，感谢您的善意（数值取 points.per_complete） */
    public void pointsCredited(Long userId, int points) {
        insert(userId, "POINTS_CREDITED", "荣誉积分到账",
                "您获得了 " + points + " 荣誉积分，感谢您的善意", "NONE", null);
    }

    /** 微光值变动：您的微光值有变动，去看看明细吧（详设 §4.4 CREDIT_CHANGED） */
    public void creditChanged(Long userId) {
        insert(userId, "CREDIT_CHANGED", "微光值变动", "您的微光值有变动，去看看明细吧", "NONE", null);
    }

    /** 邻居已出发：{name} 已确认开始帮忙"{title}"（详设 §4.4 SERVICE_STARTED） */
    public void serviceStarted(Long userId, String helperNickname, String orderTitle, Long orderId) {
        insert(userId, "SERVICE_STARTED", "邻居已出发",
                helperNickname + " 已确认开始帮忙\"" + orderTitle + "\"", "ORDER", orderId);
    }

    /** 别干等："{title}"的邻居临时有事，我们已为您重新寻找其他邻居（详设 §4.4 RELEASED） */
    public void released(Long userId, String orderTitle, Long orderId) {
        insert(userId, "RELEASED", "别干等，已为您重新寻找",
                "很抱歉，\"" + orderTitle + "\"的邻居临时有事，我们已为您重新寻找其他邻居", "ORDER", orderId);
    }

    /** 接单小休息：进入 {hours} 小时接单休息期（时长从 order.violation_cooldown_hours 插值） */
    public void cooldown(Long userId, int hours) {
        insert(userId, "COOLDOWN", "接单小休息",
                "您的账号进入 " + hours + " 小时接单休息期，远程的帮助依然可以照亮别人", "NONE", null);
    }

    /** 暂时没有邻居接单："{title}"还没有邻居响应，可以试试"一键重发"（详设 §4.4 EXPIRED） */
    public void expired(Long userId, String orderTitle, Long orderId) {
        insert(userId, "EXPIRED", "暂时没有邻居接单",
                "\"" + orderTitle + "\"还没有邻居响应，可以试试\"一键重发\"", "ORDER", orderId);
    }

    /** 申诉有结果了：您关于"{title}"的申诉已有处理结果，请查看（详设 §4.4 APPEAL_RESULT，jump=APPEAL） */
    public void appealResult(Long userId, String orderTitle, Long appealId) {
        insert(userId, "APPEAL_RESULT", "申诉有结果了",
                "您关于\"" + orderTitle + "\"的申诉已有处理结果，请查看", "APPEAL", appealId);
    }

    /** 您的反馈有回音了：您提交的反馈已处理，感谢您的建议（详设 §4.4 FEEDBACK_RESULT） */
    public void feedbackResult(Long userId) {
        insert(userId, "FEEDBACK_RESULT", "您的反馈有回音了", "您提交的反馈已处理，感谢您的建议", "NONE", null);
    }

    /** 管理员通知：管理员对您的账号进行了处理，请查看（账户级处置场景，详设 §4.4 ADMIN_ACTION） */
    public void adminAction(Long userId) {
        insert(userId, "ADMIN_ACTION", "管理员通知", "管理员对您的账号进行了处理，请查看", "NONE", null);
    }

    /** 协商取消有结果了：您参与的"{title}"协商取消，管理员裁决：{conclusion}（订单裁决专用模板，详设 §4.4） */
    public void adminActionOrderArbitrate(Long userId, String orderTitle, String conclusion, Long orderId) {
        insert(userId, "ADMIN_ACTION", "协商取消有结果了",
                "您参与的\"" + orderTitle + "\"协商取消，管理员裁决：" + conclusion, "ORDER", orderId);
    }

    /** 求助已取消：您正在帮忙的"{title}"已被取消（详设 §15.6 CANCEL_NOTICE） */
    public void cancelNotice(Long helperId, String orderTitle, Long orderId) {
        insert(helperId, "CANCEL_NOTICE", "求助已取消",
                "您正在帮忙的\"" + orderTitle + "\"已被取消，辛苦了，去看看吧", "ORDER", orderId);
    }

    /** 邻居想和您商量取消：{name} 想和您商量取消"{title}"（详设 §15.6 CANCEL_REQUEST） */
    public void cancelRequest(Long peerId, String initiatorNickname, String orderTitle, Long orderId) {
        insert(peerId, "CANCEL_REQUEST", "邻居想和您商量取消",
                initiatorNickname + " 想和您商量取消\"" + orderTitle + "\"，去看看吧", "ORDER", orderId);
    }

    /** 完成确认被拒绝："{title}"的完成确认被对方拒绝（详设 §15.6 CONFIRM_REJECT） */
    public void confirmReject(Long initiatorId, String orderTitle, Long orderId) {
        insert(initiatorId, "CONFIRM_REJECT", "完成确认被拒绝了",
                "\"" + orderTitle + "\"的完成确认被对方拒绝了，已进入申诉处理", "ORDER", orderId);
    }

    /** 邻居提交了申诉：{name} 就"{title}"提交了申诉（详设 §15.6 APPEAL_OPEN） */
    public void appealOpen(Long peerId, String initiatorNickname, String orderTitle, Long orderId) {
        insert(peerId, "APPEAL_OPEN", "邻居提交了申诉",
                initiatorNickname + " 就\"" + orderTitle + "\"提交了申诉，请留意处理结果", "ORDER", orderId);
    }

    /** 申诉已提交回执：您关于"{title}"的申诉已提交（详设 §15.6 APPEAL_OPEN） */
    public void appealOpenSelf(Long userId, String orderTitle, Long orderId) {
        insert(userId, "APPEAL_OPEN", "申诉已提交",
                "您关于\"" + orderTitle + "\"的申诉已提交，管理员会尽快处理", "ORDER", orderId);
    }

    /** 动态审核结果（APPROVED/PENDING/REJECTED；jump=POST，详设 §15.6） */
    public void postReviewResult(Long userId, Long postId, String result) {
        String title;
        String content;
        switch (result == null ? "" : result) {
            case "APPROVED" -> {
                title = "动态审核通过";
                content = "您的动态已通过审核，邻居们可以看到啦";
            }
            case "REJECTED" -> {
                title = "动态未通过审核";
                content = "您的动态未通过审核，请修改后再发布";
            }
            default -> {
                title = "动态审核中";
                content = "您的动态需要人工确认一下，请耐心等待";
            }
        }
        insert(userId, "POST_REVIEW_RESULT", title, content, "POST", postId);
    }

    /** 动态收到评论：{name} 评论了您的动态（jump=POST，详设 §15.6） */
    public void commentReply(Long postAuthorId, String commenterNickname, Long postId) {
        insert(postAuthorId, "COMMENT_REPLY", "收到新评论",
                commenterNickname + " 评论了您的动态，去看看吧", "POST", postId);
    }

    // ---------- N1–N4 查询与已读 ----------
    /** N1 通知列表（分页，按时间倒序） */
    public PageVO<NotificationVO> list(Long uid, Long page, Long pageSize) {
        Page<Notification> result = notificationMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<Notification>lambdaQuery()
                        .eq(Notification::getUserId, uid)
                        .orderByDesc(Notification::getId));
        return PageVO.of(result.convert(this::toVO));
    }

    /**
     * N2 首页横幅（最近 1 条未读 + 聚合）：
     * bannerText=最近 1 条（"标题：正文"），未读 >1 时尾部追加"，还有 N 条新消息"；
     * N=未读−1，上限 notify.banner_max_extra，超出时显示"{上限}+"（详设 §3.6/§4.4）。
     */
    public BannerVO banner(Long uid) {
        Notification latest = notificationMapper.selectOne(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getIsRead, 0)
                .orderByDesc(Notification::getId)
                .last("LIMIT 1"));
        BannerVO vo = new BannerVO();
        if (latest == null) {
            vo.setHasUnread(false);
            vo.setExtraCount(0);
            return vo;
        }
        long unread = notificationMapper.selectCount(Wrappers.<Notification>lambdaQuery()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getIsRead, 0));
        long extra = unread - 1;
        int cap = configService.getInt(BANNER_MAX_EXTRA_KEY, 3);
        String extraText = extra > cap ? cap + "+" : String.valueOf(extra);
        vo.setHasUnread(true);
        vo.setLatest(new LatestVO(latest.getType(), latest.getTitle(), latest.getContent(),
                latest.getJumpType(), latest.getJumpId()));
        vo.setExtraCount(extra > cap ? cap + "+" : extra);
        StringBuilder text = new StringBuilder(latest.getTitle()).append("：").append(latest.getContent());
        if (extra > 0) {
            text.append("，还有 ").append(extraText).append(" 条新消息");
        }
        vo.setBannerText(text.toString());
        return vo;
    }

    /** N3 已读（归属=本人；条件更新幂等） */
    public void read(Long uid, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getId, id)
                .eq(Notification::getUserId, uid)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    /** N4 全部已读（幂等，返回本次清零条数） */
    public ReadAllVO readAll(Long uid) {
        int cleared = notificationMapper.update(null, Wrappers.<Notification>lambdaUpdate()
                .eq(Notification::getUserId, uid)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        return new ReadAllVO(cleared);
    }

    /** B：删除本人通知（通知为临时收件箱，硬删除，无审计要求） */
    public void remove(Long uid, Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null || !notification.getUserId().equals(uid)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        notificationMapper.deleteById(id);
    }

    // ---------- 内部：落库 + 提交后异步推送 ----------

    private void insert(Long userId, String type, String title, String content, String jumpType, Long jumpId) {
        insert(userId, type, title, content, jumpType, jumpId, 1, null);
    }

    private void insert(Long userId, String type, String title, String content, String jumpType, Long jumpId,
                        Integer messageCount, String senderNickname) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setJumpType(jumpType);
        notification.setJumpId(jumpId);
        notification.setIsRead(0);
        notification.setMessageCount(messageCount == null ? 1 : messageCount);
        notification.setSenderNickname(senderNickname);
        notification.setCreatedAt(LocalDateTime.now());
        notificationMapper.insert(notification);
        dispatch(notification);
    }

    /** 在线推送：事务内注册 afterCommit（避免未提交先推送），提交后异步发；无事务时直接异步发 */
    private void dispatch(Notification notification) {
        Runnable push = () -> wsSessionRegistry.pushAsync(notification.getUserId(),
                WsEnvelope.notification(notification.getId(), notification.getType(), notification.getTitle(),
                        notification.getContent(), notification.getJumpType(), notification.getJumpId(),
                        notification.getCreatedAt()));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        vo.setId(notification.getId());
        vo.setType(notification.getType());
        vo.setTitle(notification.getTitle());
        vo.setContent(notification.getContent());
        vo.setJumpType(notification.getJumpType());
        vo.setJumpId(notification.getJumpId());
        vo.setIsRead(notification.getIsRead());
        vo.setMessageCount(notification.getMessageCount() == null ? 1 : notification.getMessageCount());
        vo.setSenderNickname(notification.getSenderNickname());
        vo.setCreatedAt(notification.getCreatedAt());
        return vo;
    }
}