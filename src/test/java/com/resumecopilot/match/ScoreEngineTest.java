package com.resumecopilot.match;

import com.resumecopilot.model.JdRequirements;
import com.resumecopilot.model.MatchDetail;
import com.resumecopilot.model.ResumeProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打分引擎单测：三态判定 + 五维公式 + 加权总分 + 评级线，全部手算对分。
 * 引擎不碰 DB/Spring，ScoreEngine(null, 0.85) 即"无 key 无语义"的生产等价形态。
 */
class ScoreEngineTest {

    private SkillDictionary dict;
    private ScoreEngine engine;

    @BeforeEach
    void setUp() {
        dict = new SkillDictionary(List.of(
                new String[]{"Java", "Core Java"},
                new String[]{"Java并发", "JUC"},
                new String[]{"Java并发", "并发编程"},
                new String[]{"Redis", "Spring Data Redis"},
                new String[]{"MyBatis", "MyBatis-Plus"}));
        engine = new ScoreEngine(null, 0.85);
    }

    // ---------- 覆盖矩阵：三态判定 ----------

    @Test
    @DisplayName("规则-精确：两侧别名归一到同一标准名判 COVERED")
    void ruleExactCovers() {
        ResumeProfile p = new ResumeProfile();
        p.getSkills().add(skill("JUC", "掌握JUC与线程池"));
        MatchDetail d = run(p, List.of(req("Java并发编程", 0)));
        assertEquals("COVERED", d.getOutcomes().get(0).getStatus());
        assertEquals("RULE", d.getOutcomes().get(0).getChannel());
        assertFalse(d.getOutcomes().get(0).getEvidences().isEmpty());
    }

    @Test
    @DisplayName("规则-包含：简历只有Java、JD要Java并发，判 WEAK 而非 COVERED")
    void ruleContainmentIsWeak() {
        ResumeProfile p = new ResumeProfile();
        p.getSkills().add(skill("Java", "熟悉Java基础语法"));
        MatchDetail d = run(p, List.of(req("Java并发", 0)));
        assertEquals("WEAK", d.getOutcomes().get(0).getStatus());
        assertEquals("RULE", d.getOutcomes().get(0).getChannel());
    }

    @Test
    @DisplayName("规则-经历兜底：技能列表没写但 bullet 干了，判 WEAK")
    void experienceTextFallbackIsWeak() {
        ResumeProfile p = new ResumeProfile();
        ResumeProfile.Experience e = exp("电商项目", List.of("用Redis缓存会话数据，命中率95%"));
        p.getExperiences().add(e);
        MatchDetail d = run(p, List.of(req("Redis", 0)));
        assertEquals("WEAK", d.getOutcomes().get(0).getStatus());
        assertEquals("RULE", d.getOutcomes().get(0).getChannel());
    }

    @Test
    @DisplayName("语义未配置时：MISSING 保持 MISSING，且报告标注语义通道未启用")
    void missingWhenNoSemanticChannel() {
        ResumeProfile p = new ResumeProfile();
        p.getSkills().add(skill("JUC", "掌握JUC与线程池"));
        MatchDetail d = run(p, List.of(req("Redis", 0)));
        assertEquals("MISSING", d.getOutcomes().get(0).getStatus());
        assertEquals("NONE", d.getOutcomes().get(0).getChannel());
        assertFalse(d.isSemanticChannelEnabled());
        assertTrue(d.getSemanticNote().contains("语义通道未启用"), d.getSemanticNote());
    }

    // ---------- 五维公式：手算对分 ----------

    @Test
    @DisplayName("五维公式与加权总分：一个手工样例对出全部数字")
    void fiveDimensionsHandComputation() {
        ResumeProfile p = new ResumeProfile();
        p.getSkills().add(skill("JUC", "掌握JUC与线程池"));
        ResumeProfile.Experience e = exp("电商项目",
                List.of("通过并发编程优化接口耗时30%", "负责订单模块"));
        e.getHighlights().add("通过并发编程优化接口耗时30%");
        p.getExperiences().add(e);

        // R1 经技能精确 COVERED；R2 Redis 无任何落点 MISSING
        MatchDetail d = run(p, List.of(req("Java并发编程", 0), req("Redis", 0)));

        // 技能覆盖 = 100*(covered1 + 0.5*weak0)/2 = 50
        assertEquals(50, d.getDimensions().getSkillCoverage());
        // 经历相关性：命中1（并发编程别名在 bullet 出现）/满分线 max(2, ceil(2/2))=2 -> 50
        assertEquals(50, d.getDimensions().getExperienceRelevance());
        // 年限：无 minYears 要求 -> 满分 100
        assertEquals(100, d.getDimensions().getYearsFit());
        // 项目深度：单段 min(100, 20*2 + 25*1) = 65
        assertEquals(65, d.getDimensions().getProjectDepth());
        // 表达质量：good=1（"通过并发编程优化接口耗时30%" 15字含数字），
        //          20 + 80*1/2 = 60
        assertEquals(60, d.getDimensions().getExpressionQuality());
        // 总分 = .35*50 + .25*50 + .15*100 + .15*65 + .10*60 = 60.75 -> 61
        assertEquals(61, d.getTotalScore());
        assertEquals("一般", d.getGrade());
    }

    @Test
    @DisplayName("年限维：maxMinYears=3 而经历只有 1 段（代理 1 年）得 33 分")
    void yearsDimension() {
        ResumeProfile p = new ResumeProfile();
        p.getExperiences().add(exp("项目一", List.of("完成了功能开发")));
        MatchDetail d = run(p, List.of(req("Java", 3)));
        // 100 * min(1, 1.0/3) = 33.33 -> 33
        assertEquals(33, d.getDimensions().getYearsFit());
    }

    @Test
    @DisplayName("零要求 JD：技能/经历给 50 中性分而不是 0")
    void emptyRequirementsGetNeutralScores() {
        ResumeProfile p = new ResumeProfile();
        MatchDetail d = run(p, List.of());
        assertEquals(50, d.getDimensions().getSkillCoverage());
        assertEquals(50, d.getDimensions().getExperienceRelevance());
    }

    @Test
    @DisplayName("评级线：85/70/55 三个边界")
    void gradeThresholds() {
        assertEquals("优秀", ScoreEngine.gradeOf(85));
        assertEquals("良好", ScoreEngine.gradeOf(84));
        assertEquals("良好", ScoreEngine.gradeOf(70));
        assertEquals("一般", ScoreEngine.gradeOf(69));
        assertEquals("一般", ScoreEngine.gradeOf(55));
        assertEquals("较差", ScoreEngine.gradeOf(54));
    }

    @Test
    @DisplayName("余弦相似度：平行向量 1.0、正交 0.0、零向量 0.0")
    void cosineBasics() {
        assertEquals(1.0, ScoreEngine.cosine(new float[]{1, 2}, new float[]{2, 4}), 1e-9);
        assertEquals(0.0, ScoreEngine.cosine(new float[]{1, 0}, new float[]{0, 1}), 1e-9);
        assertEquals(0.0, ScoreEngine.cosine(new float[]{0, 0}, new float[]{1, 1}), 1e-9);
        assertEquals(0.0, ScoreEngine.cosine(new float[]{1}, new float[]{1, 2}), 1e-9);
    }

    /** 被丢弃技能原样进报告留痕 */
    @Test
    @DisplayName("discardedSkills 写入 detail（幻觉防线的运行留痕）")
    void discardedSkillsRecorded() {
        ResumeProfile p = new ResumeProfile();
        MatchDetail d = engine.score(p, List.of("Go", "Kafka"), new JdRequirements(), dict,
                new MatchDetail.Weights(), "v1", "gpt-test");
        assertEquals(List.of("Go", "Kafka"), d.getDiscardedSkills());
        assertEquals("v1", d.getPromptVersion());
    }

    // ---------- helpers ----------

    private MatchDetail run(ResumeProfile p, List<JdRequirements.Requirement> reqs) {
        JdRequirements jd = new JdRequirements();
        jd.setRequirements(reqs);
        return engine.score(p, List.of(), jd, dict, new MatchDetail.Weights(), "v1", "gpt-test");
    }

    private static JdRequirements.Requirement req(String skill, int minYears) {
        JdRequirements.Requirement r = new JdRequirements.Requirement();
        r.setSkill(skill);
        r.setDescription(skill + "相关经验");
        r.setMinYears(minYears);
        return r;
    }

    private static ResumeProfile.Skill skill(String name, String evidence) {
        ResumeProfile.Skill s = new ResumeProfile.Skill();
        s.setName(name);
        s.setLevel("熟悉");
        s.setEvidence(evidence);
        return s;
    }

    private static ResumeProfile.Experience exp(String title, List<String> bullets) {
        ResumeProfile.Experience e = new ResumeProfile.Experience();
        e.setType("项目");
        e.setTitle(title);
        e.getBullets().addAll(bullets);
        return e;
    }
}
