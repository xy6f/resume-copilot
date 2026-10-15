package com.resumecopilot.match;

import com.resumecopilot.model.ResumeProfile;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 规则通道降级抽取（无 OPENAI_API_KEY 时的"能跑"路径，也是评测的确定性被测对象）。
 *
 * <p>它不是 ResumeExtractionService 的仿品，只保证两件事：
 * 1. 技能：逐行在别名字典里做包含匹配——每行的 evidence 就是行原文本身，
 *    天然满足 evidence 校验（自产数据过自己的闸，口径一致）；
 * 2. 经历：识别多个经历标题（实习经历/项目经历/竞赛/社团 及 Internship、Projects、
 *    Competition、Club 等英文等价写法），每个标题开一段、下一个标题出现即收口上一段，
 *    段内以 - * 圆点等符号开头的行收为该段 bullet，含数字的 bullet 进该段 highlights。
 *    title 用真实标题文本（历史上的"规则通道降级抽取"占位名已废弃：双段简历
 *    塌成一张占位卡会让前端经历卡片与打分口径都失真）。</p>
 *
 * <p>边界如实声明：版式复杂（多栏、无标记符号）的简历它抽不动——这正是
 * README 里"无 key 时降级路径质量有限、真实场景请配 key 走 LLM 抽取"的依据。
 * 抽取用标准技能名而不是行里的原始写法：降级路径直接对齐字典口径，
 * 免得"抽取归一"和"匹配归一"两层各写一遍规则漂移。</p>
 */
public final class LexicalResumeExtractor {

    /**
     * 经历段标题关键词 -> 经历类型（实习/项目/竞赛/社团/工作）。
     * 匹配按插入序先到先得：更具体的关键词在前
     * （如 "Internship Experience" 先命中 internship 归实习，而不是 experience 归经历）。
     */
    private static final Map<String, String> SECTION_KEYWORDS = buildSectionKeywords();

    /**
     * 标题行最大长度：标题要"短"，避免把正文长句误判成标题。
     * 由 20 放宽到 30，容纳 "Internship Experience"（21 字符）这类英文标题。
     */
    private static final int MAX_HEADING_LEN = 30;

    private static Map<String, String> buildSectionKeywords() {
        Map<String, String> m = new LinkedHashMap<>();
        // 中文族
        m.put("实习", "实习");
        m.put("项目", "项目");
        m.put("竞赛", "竞赛");
        m.put("比赛", "竞赛");
        m.put("社团", "社团");
        m.put("工作", "工作");
        m.put("职业经历", "工作");
        m.put("科研", "科研");
        m.put("经历", "经历"); // 兜底泛称，放中文族末尾
        // 英文族（比对时标题先转小写）
        m.put("internship", "实习");
        m.put("intern", "实习");
        m.put("project", "项目");
        m.put("competition", "竞赛");
        m.put("contest", "竞赛");
        m.put("club", "社团");
        m.put("extracurricular", "社团");
        m.put("work experience", "工作");
        m.put("employment", "工作");
        m.put("research", "科研");
        m.put("experience", "经历"); // 兜底泛称，放英文族末尾
        return Collections.unmodifiableMap(m);
    }

    /** 全部经历段的关键词视图：只读，供调试/单测引用 */
    public static Set<String> sectionKeywords() {
        return SECTION_KEYWORDS.keySet();
    }

    private LexicalResumeExtractor() {
    }

    public static ResumeProfile extract(String rawText, SkillDictionary dict) {
        ResumeProfile profile = new ResumeProfile();
        if (rawText == null || rawText.isBlank()) {
            return profile;
        }
        String[] lines = rawText.split("\r?\n");

        // 1. 技能：逐行扫描字典（同一标准名只记首个出处行，evidence=行原文）
        Set<String> seenStandard = new LinkedHashSet<>();
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            for (String standard : dict.extractStandards(SkillDictionary.normalize(t))) {
                if (seenStandard.add(standard)) {
                    ResumeProfile.Skill s = new ResumeProfile.Skill();
                    s.setName(standard);
                    s.setLevel("");
                    s.setEvidence(t);
                    profile.getSkills().add(s);
                }
            }
        }

        // 2. 经历：每个命中的标题行开一段新 Experience，遇到下一个标题先收口上一段；
        //    段内以 - * 开头（或圆点符号）的行收为 bullet，含数字的 bullet 进本段 highlights。
        //    首段标题之前的 bullet 不收（历史行为一致：无归属的列表项不进经历）。
        ResumeProfile.Experience current = null;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            String type = headingTypeOf(t);
            if (type != null) {
                flush(profile, current);
                current = newSection(t, type);
                continue;
            }
            if (current != null && isBullet(t)) {
                String body = stripBulletMark(t);
                current.getBullets().add(body);
                // 量化数据点：bullet 含数字即候选（计划书 5.1 highlights 的粗规则版）
                if (body.chars().anyMatch(Character::isDigit)) {
                    current.getHighlights().add(body);
                }
            }
        }
        flush(profile, current);
        return profile;
    }

    /** 段收口：只有真收到 bullet 的段才值得进 experiences（空标题段是噪声） */
    private static void flush(ResumeProfile profile, ResumeProfile.Experience section) {
        if (section != null && !section.getBullets().isEmpty()) {
            profile.getExperiences().add(section);
        }
    }

    /** 新建一段经历：title 用真实标题行原文，type 由标题关键词决定 */
    private static ResumeProfile.Experience newSection(String headingLine, String type) {
        ResumeProfile.Experience exp = new ResumeProfile.Experience();
        exp.setType(type);
        exp.setTitle(headingLine);
        exp.setOrg("");
        exp.setPeriod("");
        return exp;
    }

    /**
     * 标题判定：短行（不超 {@link #MAX_HEADING_LEN}）且包含经历关键词之一，返回对应类型；
     * 不是标题返回 null。bullet 行（以列表符号开头）永远不算标题——
     * "- 参与社团项目" 这类带关键词的列表项应归当前段的正文，而不是开新段。
     */
    private static String headingTypeOf(String line) {
        if (isBullet(line) || line.length() > MAX_HEADING_LEN) {
            return null;
        }
        String lower = line.toLowerCase();
        for (Map.Entry<String, String> e : SECTION_KEYWORDS.entrySet()) {
            if (lower.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    private static boolean isBullet(String line) {
        return line.startsWith("-") || line.startsWith("*") || line.startsWith("•")
                || line.startsWith("·");
    }

    private static String stripBulletMark(String line) {
        return line.substring(1).trim();
    }
}
