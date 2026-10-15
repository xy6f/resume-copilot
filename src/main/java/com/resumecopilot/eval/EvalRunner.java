package com.resumecopilot.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.match.EvidenceValidator;
import com.resumecopilot.match.LexicalResumeExtractor;
import com.resumecopilot.match.SkillDictionary;
import com.resumecopilot.model.ResumeProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评测跑批（计划书 4 章 eval/，"本项目的灵魂"）。
 *
 * <p>被测对象是确定性部分——规则通道抽取 + evidence 后置校验，全程不调模型：
 * 每个用例走 LexicalResumeExtractor（与 /raw 接口同一份代码），对照金标准算
 * 字段级精确率/召回率；再把"真抽出的技能 + 一条人为注入的幻觉技能"整包喂给
 * EvidenceValidator，考察"合法证据全留住、非法证据全拦下"两个方向。
 * 这让无 key 环境也能出可复现的指标数字；LLM 抽取版本的评测是同一套指标、
 * 换一个被测函数（README"真实评测接入步骤"）。</p>
 *
 * <p>纯 Java 构造（注入字典与 ObjectMapper），单测 EvalRunnerTest 直接 new，
 * 不拉 Spring 上下文——跑批逻辑与装配无关。</p>
 */
public class EvalRunner {

    /** 注入用幻觉样本的技能名：合成简历绝不含这些词，出现即证据不合法 */
    private static final String FAKE_SKILL = "不存在的技能Xyz";
    private static final String FAKE_EVIDENCE = "精通不存在的技能Xyz并主导其架构";

    private final SkillDictionary dictionary;
    private final ObjectMapper objectMapper;

    public EvalRunner(SkillDictionary dictionary, ObjectMapper objectMapper) {
        this.dictionary = dictionary;
        this.objectMapper = objectMapper;
    }

    /**
     * 跑一批用例，返回汇总报告（可直接 JSON 序列化给接口）。
     * promptVersion 贯穿：指标必须挂版本，"v1 的 0.9"和"v3 的 0.9"是两个事实。
     */
    public Map<String, Object> run(List<GoldenCase> cases, String promptVersion) {
        List<Map<String, Object>> perCase = new ArrayList<>();
        int expectedTotal = 0, hitTotal = 0, predictedTotal = 0;
        int bulletCases = 0, bulletExactCases = 0;
        int legitKept = 0, legitTotal = 0, fakeRejected = 0, fakeTotal = 0;

        for (GoldenCase c : cases) {
            ResumeProfile profile = LexicalResumeExtractor.extract(c.getResumeText(), dictionary);

            // ---- 技能口径：predicted=抽出的标准名集合（去重）----
            Set<String> predicted = new LinkedHashSet<>();
            for (ResumeProfile.Skill s : profile.getSkills()) {
                predicted.add(s.getName());
            }
            Set<String> expected = new LinkedHashSet<>(c.getExpectedSkills());
            int hit = 0;
            for (String e : expected) {
                if (predicted.contains(e)) {
                    hit++;
                }
            }
            expectedTotal += expected.size();
            predictedTotal += predicted.size();
            hitTotal += hit;

            // ---- 经历条目数：lexical 通道的 bullet 总数对人工标注数 ----
            // 分经历段抽取后 bullet 散落在多段里，必须跨段求和而不是只看首段
            int actualBullets = 0;
            for (ResumeProfile.Experience e : profile.getExperiences()) {
                actualBullets += e.getBullets().size();
            }
            bulletCases++;
            if (actualBullets == c.getExpectedBulletCount()) {
                bulletExactCases++;
            }

            // ---- evidence 校验双向考察：合法证据必须全留（retention）、
            //      注入幻觉必须全拦（rejection）。合成简历的行原文天然是子串，
            //      所以"合法全留"考的是校验器别误杀；"注入全拦"考的是校验器别放过 ----
            ResumeProfile probe = new ResumeProfile();
            probe.getSkills().addAll(profile.getSkills());
            ResumeProfile.Skill fakeSkill = new ResumeProfile.Skill();
            fakeSkill.setName(FAKE_SKILL);
            fakeSkill.setLevel("精通");
            fakeSkill.setEvidence(FAKE_EVIDENCE);
            probe.getSkills().add(fakeSkill);
            legitTotal += profile.getSkills().size();
            fakeTotal += 1;
            List<String> discarded = EvidenceValidator.sanitize(probe, c.getResumeText());
            if (discarded.contains(FAKE_SKILL)) {
                fakeRejected++;
            }
            legitKept += profile.getSkills().size() - countDiscarded(discarded, profile);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", c.getId());
            row.put("expectedSkills", expected);
            row.put("predictedSkills", predicted);
            row.put("missedSkills", minus(expected, predicted));
            row.put("extraSkills", minus(predicted, expected));
            row.put("recall", rate(hit, expected.size()));
            row.put("precision", rate(hit, predicted.size()));
            row.put("expectedBulletCount", c.getExpectedBulletCount());
            row.put("actualBulletCount", actualBullets);
            row.put("fakeSkillRejected", discarded.contains(FAKE_SKILL));
            perCase.add(row);
        }

        Map<String, Object> aggregate = new LinkedHashMap<>();
        aggregate.put("skillRecall", rate(hitTotal, expectedTotal));
        aggregate.put("skillPrecision", rate(hitTotal, predictedTotal));
        aggregate.put("experienceCountAccuracy", rate(bulletExactCases, bulletCases));
        aggregate.put("evidenceRetentionRate", rate(legitKept, legitTotal));
        aggregate.put("hallucinationRejectionRate", rate(fakeRejected, fakeTotal));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("promptVersion", promptVersion);
        report.put("aliasDictionarySize", dictionary.size());
        report.put("caseCount", cases.size());
        report.put("channel", "RULE-ONLY(deterministic)");
        report.put("aggregate", aggregate);
        report.put("cases", perCase);
        return report;
    }

    /** 被丢弃的合法技能数：discarded 里除注入样本外的都算误杀 */
    private int countDiscarded(List<String> discarded, ResumeProfile original) {
        Set<String> originalNames = new LinkedHashSet<>();
        for (ResumeProfile.Skill s : original.getSkills()) {
            originalNames.add(s.getName());
        }
        int killed = 0;
        for (String d : discarded) {
            if (originalNames.contains(d)) {
                killed++;
            }
        }
        return killed;
    }

    private static Set<String> minus(Set<String> a, Set<String> b) {
        Set<String> out = new LinkedHashSet<>(a);
        out.removeAll(b);
        return out;
    }

    /** 比率：分母 0 记 1.0（"没有可错的"不是错误）；保留 4 位小数进报告 */
    private static double rate(int num, int den) {
        double v = den == 0 ? 1.0 : (double) num / den;
        return Math.round(v * 10000.0) / 10000.0;
    }

    /**
     * 加载金标准文件。三级解析顺序（application.yml 注释同一口径）：
     * 1. classpath: 前缀走类路径（打进 jar 或在测试类路径时命中）；
     * 2. 直接当文件路径试（绝对/相对进程工作目录）；
     * 3. 兜底 src/test/resources/eval/golden.jsonl——mvn spring-boot:run 从
     *    项目根启动时恰好命中。全失败时报错把候选位置都列出来，别让使用者猜。
     */
    public List<GoldenCase> loadGolden(String location) {
        InputStream in = open(location);
        if (in == null) {
            File fallback = new File("src/test/resources/eval/golden.jsonl");
            if (fallback.isFile()) {
                in = openFile(fallback);
            }
        }
        if (in == null) {
            throw new IllegalStateException("找不到金标准文件，尝试过: [" + location
                    + "] 的 classpath/文件系统形态，以及 src/test/resources/eval/golden.jsonl");
        }
        List<GoldenCase> cases = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                if (line.isBlank() || line.trim().startsWith("//")) {
                    continue;
                }
                try {
                    cases.add(objectMapper.readValue(line, GoldenCase.class));
                } catch (Exception e) {
                    throw new IllegalStateException("golden.jsonl 第 " + lineNo + " 行解析失败: "
                            + e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("金标准读取失败", e);
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException("金标准文件为空: " + location);
        }
        return cases;
    }

    private InputStream open(String location) {
        if (location == null || location.isBlank()) {
            return null;
        }
        String loc = location.trim();
        if (loc.startsWith("classpath:")) {
            String path = loc.substring("classpath:".length());
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            return Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
        }
        File f = new File(loc);
        return f.isFile() ? openFile(f) : null;
    }

    private InputStream openFile(File f) {
        try {
            return new FileInputStream(f);
        } catch (Exception e) {
            return null;
        }
    }
}
