package com.resumecopilot.match;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 技能别名字典（计划书 5.2 规则通道的载体）：t_skill_alias 的内存形态。
 *
 * <p>纯 Java、无 Spring/DB 依赖——单测直接喂 List 构造（SkillDictionaryTest），
 * 生产路径由 {@link SkillDictionaryService} 从库里装载。匹配全部归一到
 * "标准技能名"口径：JUC、Java并发编程、多线程与并发 统统等于 Java并发。</p>
 *
 * <p>归一化（{@link #normalize}）刻意做激进清洗：小写 + 去空白 + 去 -_ / 等分隔符，
 * 这样 "MyBatis-Plus" 与 "MybatisPlus" 与 "mybatis plus" 都落到同一个键。
 * 代价是丢掉了大小写语义（"Java"与"java"视为同词），对技能名场景可接受。</p>
 */
public class SkillDictionary {

    /**
     * 参与"包含式"模糊匹配的最小别名长度：两三个字母的短词（如假设收录的 "MP"）
     * 是普通英文词的子串，误命中 sample/RabbitMQ 之类。种子数据已按此原则取舍，
     * 这里再兜一层，字典被人工加脏数据也不炸。
     */
    public static final int MIN_CONTAIN_LEN = 4;

    /** 逐行扫描（{@link #extractStandards}）用的最小长度：比上值宽 1，照顾 "K8s" 这类三字符硬别名 */
    public static final int MIN_EXTRACT_LEN = 3;

    /** normalizedAlias -> standardName（标准名自身也注册为键，所以 "Redis" 能查到 "Redis"） */
    private final Map<String, String> aliasToStandard = new LinkedHashMap<>();

    /** 按长度降序的别名键：包含式匹配时"最长键优先"，越长的别名越具体、命中越可信 */
    private final List<String> sortedKeys = new ArrayList<>();

    /**
     * @param mappings 每行 {standardName, alias}，对应 t_skill_alias 的一行
     */
    public SkillDictionary(List<String[]> mappings) {
        for (String[] row : mappings) {
            String standard = row[0].trim();
            register(standard, standard);
            if (row.length > 1 && row[1] != null && !row[1].isBlank()) {
                register(standard, row[1].trim());
            }
        }
        List<String> keys = new ArrayList<>(aliasToStandard.keySet());
        keys.sort((a, b) -> a.length() != b.length()
                ? Integer.compare(b.length(), a.length())        // 长的在前
                : a.compareTo(b));                               // 同长按字典序，保证确定性
        sortedKeys.addAll(keys);
    }

    private void register(String standard, String alias) {
        String key = normalize(alias);
        if (!key.isEmpty()) {
            aliasToStandard.putIfAbsent(key, standard);
        }
    }

    /** 归一化：小写 + 去掉所有空白（含全角空格）与 - _ / · 等分隔符 */
    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) {
            if (Character.isWhitespace(c) || c == '\u3000'
                    || c == '-' || c == '_' || c == '/' || c == '.' || c == '\u00b7') {
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    /**
     * 把一个技能写法归一到标准名。三级判定，可信度依次递减：
     * 1. 精确：归一化后就是字典里的某个键，直接给标准名；
     * 2. 包含（写法里含别名）："高并发编程实战" 含别名 "高并发编程" -> Java并发；
     * 3. 被包含（别名里含写法）："java并发" 被别名 "java并发编程" 包含 -> Java并发；
     * 都不中则返回归一化原文——未知技能保持自己，跨侧比较时仍可按同词精确命中。
     */
    public String canonical(String skillName) {
        String n = normalize(skillName);
        if (n.isEmpty()) {
            return n;
        }
        String exact = aliasToStandard.get(n);
        if (exact != null) {
            return exact;
        }
        for (String key : sortedKeys) {
            if (key.length() >= MIN_CONTAIN_LEN && n.contains(key)) {
                return aliasToStandard.get(key);
            }
        }
        if (n.length() >= MIN_CONTAIN_LEN) {
            for (String key : sortedKeys) {
                if (key.contains(n)) {
                    return aliasToStandard.get(key);
                }
            }
        }
        return n;
    }

    /** 规则通道"覆盖"判据：两侧归一到同一标准名（或同为未收录词且写法一致） */
    public boolean sameStandard(String a, String b) {
        return canonical(a).equals(canonical(b));
    }

    /**
     * 规则通道"弱覆盖"判据：两个标准名存在包含关系但不相等。
     * 典型：JD 要求 Java并发、简历只有 Java——方向正确但粒度不足，算弱覆盖。
     * 限定短的一侧至少 3 个归一化字符，防止单字/双字词乱咬。
     */
    public boolean subsuming(String canonA, String canonB) {
        String a = normalize(canonA);
        String b = normalize(canonB);
        if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
            return false;
        }
        String shorter = a.length() <= b.length() ? a : b;
        String longer = a.length() <= b.length() ? b : a;
        return shorter.length() >= 3 && longer.contains(shorter);
    }

    /**
     * 词典命中判定：某段文本（已归一化）是否体现了一个标准技能——
     * 标准名本身、或它的任何足够长的别名出现在文本里即算体现。
     * 用于"经历文本兜底"（技能列表没写、但 bullet 里干了）与 lexical 降级抽取。
     */
    public List<String> extractStandards(String normalizedText) {
        Set<String> hits = new LinkedHashSet<>();
        for (String key : sortedKeys) {
            if (key.length() >= MIN_EXTRACT_LEN && normalizedText.contains(key)) {
                hits.add(aliasToStandard.get(key));
            }
        }
        return new ArrayList<>(hits);
    }

    /** 标准名 -> 该标准名下所有别名键（含自身），供"描述文本兜底命中"用 */
    public List<String> aliasesOfStandard(String standardName) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, String> e : aliasToStandard.entrySet()) {
            if (e.getValue().equals(standardName)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 字典规模（别名条数）：写进报告 detail_json，规则通道可复现性描述的一部分 */
    public int size() {
        return aliasToStandard.size();
    }

    public Set<String> standardNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(aliasToStandard.values()));
    }
}
