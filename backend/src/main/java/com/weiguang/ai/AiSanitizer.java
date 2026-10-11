package com.weiguang.ai;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 脱敏器（详设 §5.1）：AI 调用前统一执行，五类占位符规则。
 * 手机号 / 身份证号（18 位）/ 银行卡号（16-19 位数字）/ 验证码（"验证码"上下文中的 4-8 位数字）/
 * 详细门牌号（数字+号/栋/单元/室）→ 统一替换为 [已隐去]；不做姓名脱敏。
 */
@Component
public class AiSanitizer {

    private static final String MASK = "[已隐去]";
    /** 身份证 18 位（先于银行卡处理，避免被 16-19 位数字规则误判） */
    private static final Pattern ID_CARD = Pattern.compile("\\d{17}[\\dXx]");
    /** 手机号 */
    private static final Pattern PHONE = Pattern.compile("1[3-9]\\d{9}");
    /** 银行卡号 16-19 位数字 */
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");
    /** 验证码：紧邻"验证码"三字之后（间隔 0-12 个非数字字符）的 4-8 位数字 */
    private static final Pattern VERIFY_CODE = Pattern.compile("(验证码[^0-9]{0,12})(\\d{4,8})");
    /** 详细门牌号：数字 + 号/栋/单元/室 */
    private static final Pattern DOOR = Pattern.compile("\\d+\\s*(?:单元|号|栋|室)");

    public String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String masked = ID_CARD.matcher(text).replaceAll(MASK);
        masked = PHONE.matcher(masked).replaceAll(MASK);
        masked = BANK_CARD.matcher(masked).replaceAll(MASK);
        masked = VERIFY_CODE.matcher(masked).replaceAll("$1" + MASK);
        masked = DOOR.matcher(masked).replaceAll(MASK);
        return masked;
    }
}