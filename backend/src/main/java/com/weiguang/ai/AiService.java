package com.weiguang.ai;

import com.weiguang.ai.dto.ReviewResult;
import com.weiguang.ai.dto.ThankLetterDraftResult;
import com.weiguang.ai.dto.UnderstandHelpResult;

/**
 * AI 网关（详设 §5.1）：本项目唯一出域点，负责脱敏 → 调用 → Schema 校验 → 标签词表收敛 → 降级 → 元数据日志。
 * 业务代码只依赖本接口；三个实现由 ai.mode / ai.real-impl 切换。
 */
public interface AiService {

    /**
     * 能力一 · 求助理解（详设 §5.2）：把一句话描述整理为类型/标签/紧急度/规范标题。
     * 任何异常都不上抛业务层，改为返回降级结果并置 degraded=true。
     */
    UnderstandHelpResult understandHelp(String text);

    /**
     * 能力二 · 善意守护（详设 §5.3）：复核"索要敏感信息/诱导转账"风险。
     * 入参 direction=HELPER_TO_REQUESTER / REQUESTER_TO_HELPER（脱敏后调用）；
     * 任何异常不上抛：返回降级结果并置 degraded=true（管线据此维持 HIGHLIGHT 不升级）。
     */
    ReviewResult reviewChat(String text, String direction);

    /**
     * 能力三 · 温暖表达（详设 §5.4）：以受助者口吻生成感谢信初稿。
     * 入参=求助标题+双方称呼（脱敏后调用）；任何异常不上抛，降级为本地兜底模板并置 degraded=true。
     */
    ThankLetterDraftResult draftThankLetter(String orderTitle, String requesterName, String helperName);
}
