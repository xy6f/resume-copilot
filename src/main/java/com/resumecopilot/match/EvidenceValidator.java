package com.resumecopilot.match;

import com.resumecopilot.model.ResumeProfile;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 幻觉后置校验（计划书 5.3"防 LLM 虚构没写过的经历"的最后一道闸，独立类便于重点单测）。
 *
 * <p>规则只有一条，且刻意严：技能的 evidence 必须是简历原文 rawText 的子串
 * （空白归一化之后比对）。为什么空白归一化：PDF 抽取的行尾换行、全角空格、
 * 词内空格是排版噪声不是内容差异；除此之外不做任何宽容——不忽略大小写、
 * 不做编辑距离。宁可在排版差异外一律判"无证据"丢弃，也不给幻觉放行：
 * 丢一条真技能用户可以手工补（PUT /api/resumes/{id}/profile），
 * 留一条假技能会顺着匹配报告流进改写建议，成本不对称。</p>
 *
 * <p>被丢弃的技能名会进报告 detail_json 的 discardedSkills（计划书 5.3 防线的运行留痕）。</p>
 */
public final class EvidenceValidator {

    private EvidenceValidator() {
        // 工具类：静态入口给单测和两处生产路径（摄取后、匹配前）共用同一份口径
    }

    /** 去掉所有空白字符（半角/全角/换行/制表），只留内容字符 */
    public static String stripWhitespace(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (!Character.isWhitespace(c) && c != '　') {
                b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * 单条 evidence 是否可信：非空，且空白归一化后是 rawText 的子串。
     * rawText 为 null/空时一律 false——没原文就没有事实源，一切证据都是幻觉。
     */
    public static boolean isValidEvidence(String rawText, String evidence) {
        String e = stripWhitespace(evidence);
        if (e.isEmpty()) {
            return false;
        }
        return stripWhitespace(rawText).contains(e);
    }

    /**
     * 就地清洗一份画像：技能 evidence 不合格的丢弃，返回被丢弃的技能名列表。
     * 就地改而不克隆：调用方（MatchService/ResumeIngestService）本来就是拿完即用的
     * 短生命周期对象，克隆反而制造"两份画像哪份是真的"的第二问题。
     */
    public static List<String> sanitize(ResumeProfile profile, String rawText) {
        List<String> discarded = new ArrayList<>();
        if (profile == null || profile.getSkills() == null) {
            return discarded;
        }
        Iterator<ResumeProfile.Skill> it = profile.getSkills().iterator();
        while (it.hasNext()) {
            ResumeProfile.Skill s = it.next();
            if (s == null || !isValidEvidence(rawText, s.getEvidence())) {
                discarded.add(s == null ? "(null)" : s.getName());
                it.remove();
            }
        }
        return discarded;
    }
}
