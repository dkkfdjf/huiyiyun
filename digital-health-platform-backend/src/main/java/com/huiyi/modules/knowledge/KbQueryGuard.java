package com.huiyi.modules.knowledge;

import java.util.Set;

/**
 * 知识库提问有效性判定:识别「故意浪费 API key 的无效对话」——垃圾输入(空/单字/纯标点数字/全同字符/测试乱码)。
 *
 * <p>两处用途:
 * <ul>
 *   <li>{@link KnowledgeController#ask} 在调用嵌入 + LLM <b>之前</b>硬拦截,直接返回 canned 回复 → 不烧 key(核心价值);</li>
 *   <li>{@link KbChatLogService#record} 据此打 flag_reason=「无效提问」(仍落日志供管理员审计谁在刷)。</li>
 * </ul>
 *
 * <p>判定偏保守:正常医疗/业务提问(含 ≥ {@value #MIN_MEANINGFUL} 个字母字符)一律放行,极低误判。
 * 词表 {@link #GARBAGE} 可按需增删。
 */
public final class KbQueryGuard {

    private KbQueryGuard() {}

    /** 有效(字母,含中文)字符最少数量:低于此值视为无效(空/单字/纯标点/纯数字)。 */
    static final int MIN_MEANINGFUL = 2;

    /** 全同字符判定阈值:有效部分长度 ≥ 此值且全是同一字符 → 无意义(如「哈哈哈」「aaaa」)。 */
    static final int ALL_SAME_MIN = 3;

    /** 明显的测试 / 乱码 token(与小写化原文比对)。 */
    static final Set<String> GARBAGE = Set.of(
            "test", "测试", "测试中", "试试", "试一下", "试一試",
            "aaa", "aaaa", "aa", "asdf", "qwer", "qwerty", "zxcv", "zxcvbn",
            "111", "1111", "123", "1234", "12345", "123456",
            "null", "none", "<script>"
    );

    /** 是否为无效提问(应门控拦截、不调嵌入/LLM)。 */
    public static boolean isInvalid(String raw) {
        if (raw == null) return true;
        String s = raw.trim();
        if (s.isEmpty()) return true;
        if (GARBAGE.contains(s.toLowerCase())) return true;
        String meaningful = meaningful(s);
        if (meaningful.length() < MIN_MEANINGFUL) return true;                 // 纯标点/纯数字/单字
        if (meaningful.length() >= ALL_SAME_MIN && allSame(meaningful)) return true;  // 全同字符
        return false;
    }

    /** 抽取「有效字符」:仅保留字母(中文也是 letter),剔除数字/标点/空白/emoji,用于度量有效长度。 */
    private static String meaningful(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetter(c)) sb.append(c);
        }
        return sb.toString();
    }

    private static boolean allSame(String s) {
        char first = s.charAt(0);
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) != first) return false;
        }
        return true;
    }
}
