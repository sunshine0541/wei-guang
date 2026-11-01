package com.weiguang.modules.assistant.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.AssistantResult;
import com.weiguang.ai.dto.UnderstandHelpResult;
import com.weiguang.common.enums.TimeExpect;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.modules.assistant.dto.AddressOptionVO;
import com.weiguang.modules.assistant.dto.AssistantChatRequest;
import com.weiguang.modules.assistant.dto.AssistantChatResponse;
import com.weiguang.modules.assistant.dto.AssistantConfirmRequest;
import com.weiguang.modules.assistant.dto.AssistantDraftUpdateRequest;
import com.weiguang.modules.assistant.dto.AssistantSession;
import com.weiguang.modules.assistant.dto.PublishDraftVO;
import com.weiguang.modules.order.dto.PublishRequest;
import com.weiguang.modules.order.dto.PublishVO;
import com.weiguang.modules.order.service.OrderService;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.dto.AddressVO;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import com.weiguang.modules.user.service.AddressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * AI 助手（能力五）：问答 + 发布草稿槽位补齐 + 确认后调用现有发布逻辑。
 * 约束：AI 不直接写库；必须经过确认令牌；发布校验全部复用 OrderService.publish。
 */
@Slf4j
@Service
public class AssistantService {

    private static final int MESSAGE_MAX_LENGTH = 500;
    private static final String STAGE_COLLECTING = "COLLECTING";
    private static final String STAGE_WAITING_CONFIRM = "WAITING_CONFIRM";
    private static final Set<String> CONFIRM_WORDS = Set.of("确认发布", "确认", "是的", "是", "好", "帮我发布", "发布吧");
    private static final Set<String> CANCEL_WORDS = Set.of("取消", "算了", "不发了", "重新填写", "重来");
    private static final Set<String> ORDER_TYPES = Set.of("HOME_VISIT", "ACCOMPANY", "REMOTE");
    private static final int TITLE_MAX_LENGTH = 20;
    /** “帮我发布/我想发一条求助”这类没有具体内容的命令，不能当成求助描述 */
    private static final Pattern PUBLISH_COMMAND = Pattern.compile(
            "^(请|麻烦)?(帮我|我想|我要)?(发布|发|创建|新建)(一个|一条|一下|个)?(求助|需求|订单)?[吧呀啊呢哦？?。！!，,：:\\s]*");

    private final AiService aiService;
    private final OrderService orderService;
    private final AddressService addressService;
    private final UserProfileMapper userProfileMapper;
    private final ConfigService configService;
    private final AssistantSessionStore sessionStore;

    public AssistantService(AiService aiService,
                            OrderService orderService,
                            AddressService addressService,
                            UserProfileMapper userProfileMapper,
                            ConfigService configService,
                            AssistantSessionStore sessionStore) {
        this.aiService = aiService;
        this.orderService = orderService;
        this.addressService = addressService;
        this.userProfileMapper = userProfileMapper;
        this.configService = configService;
        this.sessionStore = sessionStore;
    }

    public AssistantChatResponse chat(Long uid, AssistantChatRequest request) {
        String message = request.getMessage() == null ? "" : request.getMessage().trim();
        if (message.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (message.length() > MESSAGE_MAX_LENGTH) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        String sessionId = normalizeSessionId(request.getSessionId());
        String key = sessionKey(uid, sessionId);
        AssistantSession session = sessionStore.get(key);
        if (session == null) {
            session = new AssistantSession();
            session.setSessionId(sessionId);
            session.setUserId(uid);
            session.setStage(STAGE_COLLECTING);
        }

        if (CANCEL_WORDS.contains(message)) {
            sessionStore.remove(key);
            return simple(sessionId, "UNKNOWN", "已取消这份求助草稿。需要重新发布时告诉我即可。", "NONE");
        }

        if (STAGE_WAITING_CONFIRM.equals(session.getStage())) {
            if (CONFIRM_WORDS.contains(message)) {
                return doConfirm(uid, session, key);
            }
            return preview(session, "当前有一份待确认的求助。请点击「确认发布」完成发布，或回复「取消」重新填写。");
        }

        // 快捷选择（地址/时间/急标）或文字回答时间/急标：不再走一次意图识别
        boolean hasSelection = request.getAddressId() != null
                || (request.getTimeExpect() != null && !request.getTimeExpect().isBlank())
                || request.getUrgentFlag() != null;
        String parsedTime = parseTimeExpect(message, null);
        boolean urgentAnswer = isUrgentAnswer(message);
        // 只有单纯的时间/急标回答才走快捷分支；完整求助描述里带“今天/急”仍按发布意图处理
        boolean textSelectionAnswer = (parsedTime != null || urgentAnswer)
                && !isQuestion(message) && !looksLikeDescription(message);
        if (hasSelection || textSelectionAnswer) {
            if (session.getDraft() == null || !isUsableDescription(session.getDraft().getDescription())) {
                session.setDraft(null);
                return askDescription(sessionId, key, session);
            }
            applySelections(session.getDraft(), request, message, parsedTime, urgentAnswer);
            return completeOrAsk(uid, session, key, "好的，已更新。");
        }

        AssistantResult ai = aiService.assistant(message);
        // 上一轮已追问描述，或这条消息本身像一件具体的求助：即使意图识别保守为 UNKNOWN，也继续整理
        boolean fallbackDescription = !isQuestion(message)
                && isUsableDescription(message)
                && (session.isAwaitingDescription() || looksLikeDescription(message));
        if (!"PUBLISH".equals(ai.getIntent()) && !fallbackDescription) {
            if (session.getDraft() != null && !isQuestion(message) && isUsableDescription(message)) {
                session.getDraft().setDescription(truncate(message, MESSAGE_MAX_LENGTH));
                session.setAwaitingDescription(false);
                return completeOrAsk(uid, session, key, "好的，已更新描述。");
            }
            return simple(sessionId, ai.getIntent(), ai.getReply(), "NONE");
        }

        String publishText = isBlank(ai.getPublishText())
                ? stripPublishCommand(message) : ai.getPublishText().trim();
        if (!isUsableDescription(publishText)) {
            return askDescription(sessionId, key, session);
        }
        String reply = "PUBLISH".equals(ai.getIntent()) ? ai.getReply() : "好的，我来帮您整理这条求助。";
        return startDraft(uid, session, key, publishText, message, reply);
    }

    /** 建立/更新草稿：类型优先显式说法，其次关键词兜底，最后用模型结果 */
    private AssistantChatResponse startDraft(Long uid, AssistantSession session, String key,
                                             String publishText, String rawMessage, String reply) {
        UnderstandHelpResult understanding = aiService.understandHelp(publishText);
        PublishDraftVO draft = session.getDraft() == null ? new PublishDraftVO() : session.getDraft();
        draft.setDescription(publishText);

        String type = parseOrderType(rawMessage);
        if (type == null) {
            type = parseOrderType(publishText);
        }
        draft.setOrderType(type == null ? understanding.getOrderType() : type);
        draft.setTitle(understanding.getTitle());
        draft.setTags(understanding.getTags());
        draft.setExtraTag(understanding.getExtraTag());
        draft.setTimeExpect(parseTimeExpect(rawMessage, draft.getTimeExpect()));
        draft.setUrgentFlag(parseUrgent(rawMessage, draft.getUrgentFlag()));
        session.setDraft(draft);
        session.setAwaitingDescription(false);
        return completeOrAsk(uid, session, key, reply);
    }

    public AssistantChatResponse confirm(Long uid, AssistantConfirmRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String key = sessionKey(uid, sessionId);
        AssistantSession session = sessionStore.get(key);
        if (session == null || !STAGE_WAITING_CONFIRM.equals(session.getStage())
                || !session.getConfirmToken().equals(request.getConfirmToken())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return doConfirm(uid, session, key);
    }

    /**
     * 恢复会话：只读取/重算当前状态，不调用 AI，不产生聊天回复。
     * 服务端会话过期返回 EXPIRED，前端据此清理本地记录。
     */
    public AssistantChatResponse restore(Long uid, String sessionId) {
        if (isBlank(sessionId)) {
            return simple("", "UNKNOWN", "", "EXPIRED");
        }
        String sid = sessionId.trim();
        String key = sessionKey(uid, sid);
        AssistantSession session = sessionStore.get(key);
        if (session == null) {
            return simple(sid, "UNKNOWN", "", "EXPIRED");
        }
        if (session.getDraft() == null) {
            return simple(sid, "UNKNOWN", "", "NONE");
        }
        AssistantChatResponse response = STAGE_WAITING_CONFIRM.equals(session.getStage())
                ? preview(session, "")
                : completeOrAsk(uid, session, key, "");
        response.setReply("");
        return response;
    }

    /**
     * 编辑草稿：改标题/描述/类型/时间/地址/急单后重新校验；旧确认令牌立即作废。
     * 发布仍必须重新确认，AI 不直接写库。
     */
    public AssistantChatResponse updateDraft(Long uid, AssistantDraftUpdateRequest request) {
        String sessionId = normalizeSessionId(request.getSessionId());
        String key = sessionKey(uid, sessionId);
        AssistantSession session = sessionStore.get(key);
        if (session == null || session.getDraft() == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        PublishDraftVO draft = session.getDraft();
        if (request.getTitle() != null) {
            String title = request.getTitle().trim();
            if (title.length() > TITLE_MAX_LENGTH) {
                throw new BizException(ErrorCode.CONTENT_TOO_LONG);
            }
            draft.setTitle(title.isEmpty() ? null : title);
        }
        if (request.getDescription() != null) {
            draft.setDescription(request.getDescription().trim());
        }
        if (request.getOrderType() != null) {
            String type = request.getOrderType().trim();
            if (!ORDER_TYPES.contains(type)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            draft.setOrderType(type);
            if ("REMOTE".equals(type)) {
                draft.setAddressId(null);
                draft.setAddressLabel(null);
            }
        }
        if (request.getTimeExpect() != null) {
            String time = request.getTimeExpect().trim();
            if (time.isEmpty()) {
                draft.setTimeExpect(null);
            } else if (!TimeExpect.isValid(time)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            } else {
                draft.setTimeExpect(time);
            }
        }
        if (request.getAddressId() != null && !"REMOTE".equals(draft.getOrderType())) {
            draft.setAddressId(request.getAddressId() > 0 ? request.getAddressId() : null);
            draft.setAddressLabel(null);
        }
        if (request.getUrgentFlag() != null) {
            if (request.getUrgentFlag() != 0 && request.getUrgentFlag() != 1) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            draft.setUrgentFlag(request.getUrgentFlag());
        }
        session.setStage(STAGE_COLLECTING);
        session.setConfirmToken(null);
        session.setAwaitingDescription(false);
        return completeOrAsk(uid, session, key, "好的，已更新。");
    }

    private AssistantChatResponse doConfirm(Long uid, AssistantSession session, String key) {
        // 原子消费确认令牌：并发 confirm 只有一个能继续，避免重复建单
        AssistantSession active = sessionStore.consume(key, session.getConfirmToken());
        if (active == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        PublishDraftVO draft = active.getDraft();
        if (draft == null || !isUsableDescription(draft.getDescription())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        try {
            PublishVO vo = orderService.publish(uid, toPublishRequest(draft));
            // 发布成功后确保 Redis 与本地都不再保留该会话
            sessionStore.remove(key);
            AssistantChatResponse response = base(active.getSessionId(), "PUBLISH", "已帮您发布这条求助。");
            response.setAction("PUBLISHED");
            response.setDraft(draft);
            response.setOrderId(vo.getOrderId());
            return response;
        } catch (BizException e) {
            // 发布失败：把会话放回，用户修正后可继续用同一令牌重试
            sessionStore.put(key, active);
            AssistantChatResponse response = base(active.getSessionId(), "PUBLISH", friendlyError(e));
            response.setAction("ERROR");
            response.setDraft(draft);
            response.setConfirmToken(active.getConfirmToken());
            return response;
        } catch (Exception e) {
            // 非业务异常也恢复会话，避免用户草稿因一次异常被吞掉
            sessionStore.put(key, active);
            throw e;
        }
    }

    private AssistantChatResponse completeOrAsk(Long uid, AssistantSession session, String key, String prefixReply) {
        PublishDraftVO draft = session.getDraft();
        List<String> missing = new ArrayList<>();
        if (!isUsableDescription(draft.getDescription())) {
            missing.add("DESCRIPTION");
        }
        if (isBlank(draft.getTitle())) {
            missing.add("TITLE");
        }
        if (isBlank(draft.getOrderType())) {
            missing.add("TYPE");
        }
        if (draft.getUrgentFlag() == null) {
            draft.setUrgentFlag(0);
        }
        if (isBlank(draft.getTimeExpect()) || !TimeExpect.isValid(draft.getTimeExpect())) {
            draft.setTimeExpect(null);
            missing.add("TIME");
        }

        int maxDesc = configService.getInt("content.desc_max_length", 200);
        if (draft.getDescription() != null && draft.getDescription().length() > maxDesc) {
            missing.add("DESCRIPTION_TOO_LONG");
        }

        List<AddressVO> addresses = addressService.list(uid);
        if ("REMOTE".equals(draft.getOrderType())) {
            draft.setAddressId(null);
            draft.setAddressLabel(null);
        } else {
            if (draft.getAddressId() != null) {
                AddressVO selected = addresses.stream()
                        .filter(item -> draft.getAddressId().equals(item.getId()))
                        .findFirst()
                        .orElse(null);
                if (selected == null) {
                    draft.setAddressId(null);
                    draft.setAddressLabel(null);
                } else {
                    setAddress(draft, selected);
                }
            }
            if (draft.getAddressId() == null) {
                if (addresses.size() == 1) {
                    setAddress(draft, addresses.get(0));
                } else {
                    missing.add("ADDRESS");
                    if (addresses.isEmpty()) {
                        missing.add("NO_ADDRESS");
                    }
                }
            }
        }
        if (!"REMOTE".equals(draft.getOrderType()) && missingEmergencyContact(uid)) {
            missing.add("EMERGENCY_CONTACT");
        }

        session.setDraft(draft);
        if (!missing.isEmpty()) {
            session.setAwaitingDescription(missing.contains("DESCRIPTION"));
            AssistantChatResponse response = base(session.getSessionId(), "PUBLISH",
                    prefixReply + " " + askFor(missing, addresses, maxDesc));
            response.setAction(actionOf(missing));
            response.setDraft(draft);
            response.setMissing(missing);
            if (missing.contains("ADDRESS")) {
                response.setAddressOptions(addresses.stream().map(AddressOptionVO::from).toList());
            }
            sessionStore.put(key, session);
            return response;
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        session.setStage(STAGE_WAITING_CONFIRM);
        session.setConfirmToken(token);
        session.setAwaitingDescription(false);
        sessionStore.put(key, session);
        return preview(session, prefixReply + " 请确认下面的信息，确认后我就帮您发布。");
    }

    private AssistantChatResponse preview(AssistantSession session, String reply) {
        AssistantChatResponse response = base(session.getSessionId(), "PUBLISH", reply);
        response.setAction("READY_TO_CONFIRM");
        response.setDraft(session.getDraft());
        response.setConfirmToken(session.getConfirmToken());
        return response;
    }

    private AssistantChatResponse simple(String sessionId, String intent, String reply, String action) {
        AssistantChatResponse response = base(sessionId, intent, reply);
        response.setAction(action);
        return response;
    }

    private AssistantChatResponse base(String sessionId, String intent, String reply) {
        AssistantChatResponse response = new AssistantChatResponse();
        response.setSessionId(sessionId);
        response.setIntent(intent);
        response.setReply(reply);
        return response;
    }

    private void applySelections(PublishDraftVO draft, AssistantChatRequest request, String message,
                                 String parsedTime, boolean urgentAnswer) {
        String timeExpect = request.getTimeExpect();
        if (timeExpect != null && !timeExpect.isBlank()) {
            if (!TimeExpect.isValid(timeExpect)) {
                throw new BizException(ErrorCode.PARAM_INVALID);
            }
            draft.setTimeExpect(timeExpect);
        } else if (parsedTime != null) {
            draft.setTimeExpect(parsedTime);
        }

        if (request.getUrgentFlag() != null) {
            draft.setUrgentFlag(request.getUrgentFlag() == 1 ? 1 : 0);
        } else if (urgentAnswer) {
            draft.setUrgentFlag(message.contains("不急") || message.contains("正常") ? 0 : 1);
        }

        if (request.getAddressId() != null) {
            draft.setAddressId(request.getAddressId());
        }
        String orderType = parseOrderType(message);
        if (orderType != null) {
            draft.setOrderType(orderType);
        }
    }

    private PublishRequest toPublishRequest(PublishDraftVO draft) {
        PublishRequest request = new PublishRequest();
        request.setSource("FREE");
        request.setOrderType(draft.getOrderType());
        request.setTitle(draft.getTitle());
        request.setDescription(draft.getDescription());
        request.setAddressId(draft.getAddressId());
        request.setTags(draft.getTags());
        request.setExtraTag(draft.getExtraTag());
        request.setTimeExpect(draft.getTimeExpect());
        request.setUrgentFlag(draft.getUrgentFlag());
        return request;
    }

    private boolean missingEmergencyContact(Long uid) {
        UserProfile profile = userProfileMapper.selectOne(Wrappers.<UserProfile>lambdaQuery()
                .eq(UserProfile::getUserId, uid)
                .last("LIMIT 1"));
        return profile == null
                || isBlank(profile.getEmergencyContactName())
                || isBlank(profile.getEmergencyContactPhone());
    }

    private void setAddress(PublishDraftVO draft, AddressVO address) {
        draft.setAddressId(address.getId());
        draft.setAddressLabel((address.getLabel() == null ? "地址" : address.getLabel())
                + "（" + (address.getAreaText() == null ? "" : address.getAreaText()) + "）");
    }

    private String askFor(List<String> missing, List<AddressVO> addresses, int maxDesc) {
        StringBuilder text = new StringBuilder();
        if (missing.contains("DESCRIPTION")) {
            text.append("请简单描述一下需要邻居帮什么忙。");
        }
        if (missing.contains("DESCRIPTION_TOO_LONG")) {
            text.append("描述有点长，请精简到 ").append(maxDesc).append(" 字以内。");
        }
        if (missing.contains("TYPE")) {
            text.append("请说明是上门、陪同外出还是远程帮忙。");
        }
        if (missing.contains("TIME")) {
            text.append("什么时候需要？可以说「现在」「今天」「明天」「本周」或「都行」。");
        }
        if (missing.contains("ADDRESS")) {
            text.append(addresses.isEmpty()
                    ? "您还没有保存地址，请先到「我的地址」添加一个。"
                    : "请选择这次要用的地址。");
        }
        if (missing.contains("EMERGENCY_CONTACT")) {
            text.append("上门/陪同求助需要先填写紧急联系人，请到「我的资料」补充。");
        }
        return text.toString();
    }

    private String actionOf(List<String> missing) {
        if (missing.contains("EMERGENCY_CONTACT")) {
            return "NEED_PROFILE";
        }
        if (missing.contains("ADDRESS")) {
            return "NEED_ADDRESS";
        }
        return "COLLECTING";
    }

    private String friendlyError(BizException e) {
        return switch (e.getCode()) {
            case ErrorCode.PENDING_LIMIT -> "您还有待响应的求助，先处理一条再发布吧。";
            case ErrorCode.CONTENT_BLOCKED -> "这段描述里有些内容暂时不能发布，换一种说法试试？";
            case ErrorCode.CONTENT_TOO_LONG -> "内容有点长，精简一下再试。";
            default -> e.getMessage() == null ? "发布没有成功，请稍后再试。" : e.getMessage();
        };
    }

    /** 只发了一条“帮我发布求助”之类的命令，没有具体求助内容 */
    private boolean isCommandOnly(String text) {
        return text != null && PUBLISH_COMMAND.matcher(text.trim()).matches();
    }

    /** 去掉“帮我发布/我想发一条求助”等命令前缀，留下用户描述 */
    private String stripPublishCommand(String text) {
        return PUBLISH_COMMAND.matcher(text == null ? "" : text.trim()).replaceFirst("").trim();
    }

    private boolean isUsableDescription(String text) {
        if (text == null) {
            return false;
        }
        String value = text.trim();
        return value.length() >= 3 && !isCommandOnly(value);
    }

    private AssistantChatResponse askDescription(String sessionId, String key, AssistantSession session) {
        session.setAwaitingDescription(true);
        sessionStore.put(key, session);
        return simple(sessionId, "PUBLISH", askDescriptionText(), "COLLECTING");
    }

    private String askDescriptionText() {
        return "请先告诉我具体需要邻居帮什么忙，比如“厨房水管漏水，今天想请人上门看看”。";
    }

    private String parseTimeExpect(String text, String current) {
        if (text == null) {
            return current;
        }
        if (text.contains("马上") || text.contains("现在") || text.contains("尽快") || text.contains("立刻")) {
            return "ASAP";
        }
        if (text.contains("今天")) {
            return "TODAY";
        }
        if (text.contains("明天")) {
            return "TOMORROW";
        }
        if (text.contains("本周") || text.contains("这周") || text.contains("周末")) {
            return "THIS_WEEK";
        }
        if (text.contains("都行") || text.contains("不限") || text.contains("随时")) {
            return "ANY";
        }
        return current;
    }

    private int parseUrgent(String text, Integer current) {
        if (text == null) {
            return current == null ? 0 : current;
        }
        if (text.contains("不急") || text.contains("正常")) {
            return 0;
        }
        if (text.contains("紧急") || text.contains("加急") || text.contains("急")) {
            return 1;
        }
        return current == null ? 0 : current;
    }

    private boolean isUrgentAnswer(String text) {
        return text != null && (text.contains("急") || text.contains("正常"));
    }

    /**
     * 类型关键词兜底：先判断是否需要远程，再判断是否上门，最后把“陪/一起/打球/下棋/散步”等归为陪同。
     * 这是对模型分类的纠偏，避免“不需要地址”就被误判成远程。
     */
    private String parseOrderType(String text) {
        if (text == null) {
            return null;
        }
        if (containsAny(text, "远程", "线上", "视频", "电话", "手机", "网上", "微信")) {
            return "REMOTE";
        }
        if (containsAny(text, "上门", "来我家", "到我家", "家里", "维修", "修", "搬", "打扫", "做饭", "洗",
                "厨房", "卫生间", "水管", "漏水", "灯", "门", "窗", "家电", "马桶")) {
            return "HOME_VISIT";
        }
        if (containsAny(text, "陪同", "外出", "一起", "陪", "散步", "公园", "逛街", "聊天", "打球", "下棋", "棋", "球", "出门")) {
            return "ACCOMPANY";
        }
        return null;
    }

    /** 像一件具体求助的描述（用于意图识别保守时兜底继续整理） */
    private boolean looksLikeDescription(String text) {
        return !isQuestion(text) && containsAny(text, "帮", "需要", "陪", "一起", "打球", "下棋", "棋", "球",
                "散步", "公园", "逛街", "聊天", "出门", "漏水", "坏", "不亮", "堵", "搬", "买", "取", "药",
                "医院", "修", "打扫", "做饭", "洗", "剪", "照顾", "教", "灯", "水", "电", "门", "窗");
    }

    private boolean isQuestion(String text) {
        return text != null && (text.contains("怎么") || text.contains("如何") || text.contains("为什么")
                || text.contains("流程") || text.contains("步骤"));
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeSessionId(String sessionId) {
        if (isBlank(sessionId)) {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        String value = sessionId.trim();
        if (value.length() > 64) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        return value;
    }

    private String sessionKey(Long uid, String sessionId) {
        return "assistant:session:" + uid + ":" + sessionId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
