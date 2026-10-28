package com.weiguang.modules.incentive.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.weiguang.ai.AiService;
import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.common.enums.OrderStatus;
import com.weiguang.common.error.ErrorCode;
import com.weiguang.common.exception.BizException;
import com.weiguang.common.result.IdVO;
import com.weiguang.common.result.PageVO;
import com.weiguang.common.util.PageUtils;
import com.weiguang.modules.incentive.dto.EvaluationRequest;
import com.weiguang.modules.incentive.dto.LetterRequest;
import com.weiguang.modules.incentive.dto.LedgerRecordVO;
import com.weiguang.modules.incentive.dto.LedgerVO;
import com.weiguang.modules.incentive.dto.NeighborEvaluationVO;
import com.weiguang.modules.incentive.dto.NeighborProfileVO;
import com.weiguang.modules.incentive.dto.StoryCardVO;
import com.weiguang.modules.incentive.dto.ThankVO;
import com.weiguang.modules.incentive.entity.CreditLedger;
import com.weiguang.modules.incentive.entity.Evaluation;
import com.weiguang.modules.incentive.entity.PointLedger;
import com.weiguang.modules.incentive.entity.StoryCard;
import com.weiguang.modules.incentive.entity.ThankLetter;
import com.weiguang.modules.incentive.mapper.CreditLedgerMapper;
import com.weiguang.modules.incentive.mapper.EvaluationMapper;
import com.weiguang.modules.incentive.mapper.PointLedgerMapper;
import com.weiguang.modules.incentive.mapper.StoryCardMapper;
import com.weiguang.modules.incentive.mapper.ThankLetterMapper;
import com.weiguang.modules.order.entity.HelpOrder;
import com.weiguang.modules.order.mapper.HelpOrderMapper;
import com.weiguang.modules.system.service.ConfigService;
import com.weiguang.modules.user.entity.User;
import com.weiguang.modules.user.entity.UserProfile;
import com.weiguang.modules.user.mapper.UserMapper;
import com.weiguang.modules.user.mapper.UserProfileMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评价/感谢信/悄悄话/故事卡/账本/感谢墙/邻居公开页（详设 §3.3 S 系、§3.6 U8–U12）。
 */
@Service
public class IncentiveService {

    private static final int ADVICE_MAX_LENGTH = 100;
    private static final int LETTER_MAX_LENGTH = 500;
    /** U11 感谢墙在邻居公开页内展示的条数（分页明细走 U10） */
    private static final int NEIGHBOR_THANKS_LIMIT = 10;

    private final HelpOrderMapper helpOrderMapper;
    private final UserMapper userMapper;
    private final UserProfileMapper userProfileMapper;
    private final EvaluationMapper evaluationMapper;
    private final ThankLetterMapper thankLetterMapper;
    private final StoryCardMapper storyCardMapper;
    private final CreditLedgerMapper creditLedgerMapper;
    private final PointLedgerMapper pointLedgerMapper;
    private final ConfigService configService;
    private final AiService aiService;

    public IncentiveService(HelpOrderMapper helpOrderMapper,
                            UserMapper userMapper,
                            UserProfileMapper userProfileMapper,
                            EvaluationMapper evaluationMapper,
                            ThankLetterMapper thankLetterMapper,
                            StoryCardMapper storyCardMapper,
                            CreditLedgerMapper creditLedgerMapper,
                            PointLedgerMapper pointLedgerMapper,
                            ConfigService configService,
                            AiService aiService) {
        this.helpOrderMapper = helpOrderMapper;
        this.userMapper = userMapper;
        this.userProfileMapper = userProfileMapper;
        this.evaluationMapper = evaluationMapper;
        this.thankLetterMapper = thankLetterMapper;
        this.storyCardMapper = storyCardMapper;
        this.creditLedgerMapper = creditLedgerMapper;
        this.pointLedgerMapper = pointLedgerMapper;
        this.configService = configService;
        this.aiService = aiService;
    }

    /** S1 评价（仅受助者、一单一评、窗口内） */
    @Transactional
    public IdVO evaluate(Long uid, Long orderId, EvaluationRequest request) {
        HelpOrder order = requireCompletedInWindow(orderId);
        if (!uid.equals(order.getRequesterId())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        if (request.getStars() == null || request.getStars() < 1 || request.getStars() > 5) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        String advice = normalize(request.getAdvice());
        if (advice != null && advice.length() > ADVICE_MAX_LENGTH) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        Evaluation evaluation = new Evaluation();
        evaluation.setOrderId(orderId);
        evaluation.setRequesterId(uid);
        evaluation.setHelperId(order.getHelperId());
        evaluation.setStars(request.getStars());
        evaluation.setAdvice(advice);
        evaluation.setCreatedAt(LocalDateTime.now());
        try {
            evaluationMapper.insert(evaluation);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        return IdVO.of(evaluation.getId());
    }

    /** S3 感谢信（仅受助者、一单一封、窗口内） */
    @Transactional
    public IdVO thankLetter(Long uid, Long orderId, LetterRequest request) {
        HelpOrder order = requireCompletedInWindow(orderId);
        if (!uid.equals(order.getRequesterId())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        String content = normalize(request.getContent());
        if (content == null) {
            throw new BizException(ErrorCode.PARAM_INVALID);
        }
        if (content.length() > LETTER_MAX_LENGTH) {
            throw new BizException(ErrorCode.CONTENT_TOO_LONG);
        }
        ThankLetter letter = new ThankLetter();
        letter.setOrderId(orderId);
        letter.setSenderId(uid);
        letter.setHelperId(order.getHelperId());
        letter.setContent(content);
        letter.setAiAssisted(Boolean.TRUE.equals(request.getAiAssisted()) ? 1 : 0);
        letter.setCreatedAt(LocalDateTime.now());
        try {
            thankLetterMapper.insert(letter);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.DUPLICATE_SUBMIT);
        }
        return IdVO.of(letter.getId());
    }

    /** S2 感谢信 AI 起头（详设 §3.3 / §5.4）：仅受助者、COMPLETED 且窗口内；不落库，前端可改可弃 */
    public ThankLetterDraftResult thankLetterDraft(Long uid, Long orderId) {
        HelpOrder order = requireCompletedInWindow(orderId);
        if (!uid.equals(order.getRequesterId())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return aiService.draftThankLetter(order.getTitle(), nicknameOf(uid), nicknameOf(order.getHelperId()));
    }

    /** S5 查看故事卡（订单双方；未生成 → 40002） */
    public StoryCardVO storyCard(Long uid, Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        requireParticipant(order, uid);
        StoryCard card = storyCardMapper.selectOne(Wrappers.<StoryCard>lambdaQuery()
                .eq(StoryCard::getOrderId, orderId));
        if (card == null) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        StoryCardVO vo = new StoryCardVO();
        vo.setRequesterName(card.getRequesterName());
        vo.setHelperName(card.getHelperName());
        vo.setThing(card.getThing());
        vo.setSummary(card.getSummary());
        vo.setCreatedAt(card.getCreatedAt());
        return vo;
    }

    /** U8 微光值余额与明细（仅本人） */
    public LedgerVO credit(Long uid, Long page, Long pageSize) {
        User user = userMapper.selectById(uid);
        Page<CreditLedger> result = creditLedgerMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<CreditLedger>lambdaQuery()
                        .eq(CreditLedger::getUserId, uid)
                        .orderByDesc(CreditLedger::getId));
        LedgerVO vo = new LedgerVO();
        vo.setBalance(user == null || user.getCreditScore() == null ? 0 : user.getCreditScore());
        vo.setRecords(result.getRecords().stream().map(item -> {
            LedgerRecordVO record = new LedgerRecordVO();
            record.setDelta(item.getDelta());
            record.setReasonCode(item.getReasonCode());
            record.setOrderId(item.getOrderId());
            record.setBalanceAfter(item.getBalanceAfter());
            record.setRemark(item.getRemark());
            record.setCreatedAt(item.getCreatedAt());
            return record;
        }).toList());
        vo.setTotal(result.getTotal());
        vo.setCurrent(result.getCurrent());
        vo.setSize(result.getSize());
        return vo;
    }

    /** U9 积分余额与明细（仅本人） */
    public LedgerVO points(Long uid, Long page, Long pageSize) {
        Page<PointLedger> result = pointLedgerMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<PointLedger>lambdaQuery()
                        .eq(PointLedger::getUserId, uid)
                        .orderByDesc(PointLedger::getId));
        LedgerVO vo = new LedgerVO();
        vo.setBalance(latestPointBalance(uid));
        vo.setRecords(result.getRecords().stream().map(item -> {
            LedgerRecordVO record = new LedgerRecordVO();
            record.setDelta(item.getDelta());
            record.setReasonCode(item.getReasonCode());
            record.setOrderId(item.getOrderId());
            record.setBalanceAfter(item.getBalanceAfter());
            record.setCreatedAt(item.getCreatedAt());
            return record;
        }).toList());
        vo.setTotal(result.getTotal());
        vo.setCurrent(result.getCurrent());
        vo.setSize(result.getSize());
        return vo;
    }

    /** U10 我收到的感谢（作为助人者） */
    public PageVO<ThankVO> thanks(Long uid, Long page, Long pageSize) {
        Page<ThankLetter> result = thankLetterMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<ThankLetter>lambdaQuery()
                        .eq(ThankLetter::getHelperId, uid)
                        .orderByDesc(ThankLetter::getId));
        return PageVO.of(result.convert(letter -> toThankVO(letter, orderTitles(List.of(letter)),
                senderNicknames(List.of(letter)))));
    }

    /** U11 邻居公开页（不含年龄/地址/小习惯/紧急联系人/微光值） */
    public NeighborProfileVO neighborProfile(Long targetUid) {
        UserProfile profile = requireNeighbor(targetUid);
        List<Evaluation> evaluations = evaluationMapper.selectList(Wrappers.<Evaluation>lambdaQuery()
                .eq(Evaluation::getHelperId, targetUid));
        NeighborProfileVO vo = new NeighborProfileVO();
        vo.setNickname(profile.getNickname());
        vo.setAvatarUrl(profile.getAvatarUrl());
        vo.setSignature(profile.getSignature());
        vo.setEvaluationCount((long) evaluations.size());
        vo.setEvaluationAvg(evaluations.isEmpty() ? null
                : evaluations.stream().mapToInt(Evaluation::getStars).average().orElse(0));
        List<ThankLetter> letters = thankLetterMapper.selectList(Wrappers.<ThankLetter>lambdaQuery()
                .eq(ThankLetter::getHelperId, targetUid)
                .orderByDesc(ThankLetter::getId)
                .last("LIMIT " + NEIGHBOR_THANKS_LIMIT));
        Map<Long, String> titles = orderTitles(letters);
        Map<Long, String> nicknames = senderNicknames(letters);
        vo.setThanks(letters.stream().map(letter -> toThankVO(letter, titles, nicknames)).toList());
        return vo;
    }

    /** U12 邻居收到的评价列表（含受助者昵称） */
    public PageVO<NeighborEvaluationVO> neighborEvaluations(Long targetUid, Long page, Long pageSize) {
        requireNeighbor(targetUid);
        Page<Evaluation> result = evaluationMapper.selectPage(
                new Page<>(PageUtils.normalizePage(page), PageUtils.normalizeSize(pageSize)),
                Wrappers.<Evaluation>lambdaQuery()
                        .eq(Evaluation::getHelperId, targetUid)
                        .orderByDesc(Evaluation::getId));
        Map<Long, String> nicknames = new HashMap<>();
        List<Long> requesterIds = result.getRecords().stream().map(Evaluation::getRequesterId).distinct().toList();
        if (!requesterIds.isEmpty()) {
            for (UserProfile profile : userProfileMapper.selectList(
                    Wrappers.<UserProfile>lambdaQuery().in(UserProfile::getUserId, requesterIds))) {
                nicknames.put(profile.getUserId(), profile.getNickname());
            }
        }
        return PageVO.of(result.convert(item -> {
            NeighborEvaluationVO vo = new NeighborEvaluationVO();
            vo.setStars(item.getStars());
            vo.setAdvice(item.getAdvice());
            vo.setRequesterNickname(nicknames.get(item.getRequesterId()));
            vo.setCreatedAt(item.getCreatedAt());
            return vo;
        }));
    }

    /* ---------- 私有工具 ---------- */

    /** S 系窗口守卫：仅 COMPLETED 且 now ≤ completed_at + order.post_action_window_days */
    private HelpOrder requireCompletedInWindow(Long orderId) {
        HelpOrder order = helpOrderMapper.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (!OrderStatus.COMPLETED.name().equals(order.getStatus())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        int windowDays = configService.getInt("order.post_action_window_days", 7);
        if (order.getCompletedAt() == null
                || order.getCompletedAt().plusDays(windowDays).isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.STATE_NOT_ALLOWED);
        }
        return order;
    }

    /** 昵称快照（AI 能力三入参"双方称呼"；昵称属公开信息，不做脱敏） */
    private String nicknameOf(Long uid) {
        UserProfile profile = userProfileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, uid));
        return profile == null || profile.getNickname() == null || profile.getNickname().isBlank()
                ? "邻居" : profile.getNickname();
    }

    private UserProfile requireNeighbor(Long targetUid) {
        User user = userMapper.selectById(targetUid);
        if (user == null || "CANCELLED".equals(user.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        UserProfile profile = userProfileMapper.selectOne(
                Wrappers.<UserProfile>lambdaQuery().eq(UserProfile::getUserId, targetUid));
        if (profile == null) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        return profile;
    }

    private void requireParticipant(HelpOrder order, Long uid) {
        if (!uid.equals(order.getRequesterId()) && !uid.equals(order.getHelperId())) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
    }

    private ThankVO toThankVO(ThankLetter letter, Map<Long, String> titles, Map<Long, String> nicknames) {
        ThankVO vo = new ThankVO();
        vo.setLetterId(letter.getId());
        vo.setOrderId(letter.getOrderId());
        vo.setTitle(titles.get(letter.getOrderId()));
        vo.setContent(letter.getContent());
        vo.setSenderNickname(nicknames.get(letter.getSenderId()));
        vo.setCreatedAt(letter.getCreatedAt());
        return vo;
    }

    private Map<Long, String> orderTitles(List<ThankLetter> letters) {
        Map<Long, String> titles = new HashMap<>();
        List<Long> orderIds = letters.stream().map(ThankLetter::getOrderId).distinct().toList();
        if (!orderIds.isEmpty()) {
            for (HelpOrder order : helpOrderMapper.selectBatchIds(orderIds)) {
                titles.put(order.getId(), order.getTitle());
            }
        }
        return titles;
    }

    private Map<Long, String> senderNicknames(List<ThankLetter> letters) {
        Map<Long, String> nicknames = new HashMap<>();
        List<Long> senderIds = letters.stream().map(ThankLetter::getSenderId).distinct().toList();
        if (!senderIds.isEmpty()) {
            for (UserProfile profile : userProfileMapper.selectList(
                    Wrappers.<UserProfile>lambdaQuery().in(UserProfile::getUserId, senderIds))) {
                nicknames.put(profile.getUserId(), profile.getNickname());
            }
        }
        return nicknames;
    }

    private int latestPointBalance(Long uid) {
        PointLedger latest = pointLedgerMapper.selectOne(Wrappers.<PointLedger>lambdaQuery()
                .eq(PointLedger::getUserId, uid)
                .orderByDesc(PointLedger::getId)
                .last("LIMIT 1"));
        return latest == null || latest.getBalanceAfter() == null ? 0 : latest.getBalanceAfter();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}