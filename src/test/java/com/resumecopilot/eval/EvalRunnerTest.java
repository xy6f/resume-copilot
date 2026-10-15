package com.resumecopilot.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.match.SeedDictionaryTestSupport;
import com.resumecopilot.match.SkillDictionary;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 评测跑批单测：golden.jsonl 全量过规则通道 + evidence 校验，对指标阈值断言。
 *
 * <p>阈值硬编码在此（不放配置）：阈值是"人对系统当前水位的要求"，
 * 调它应当是一次代码评审（改这行=改验收口径），而不是改一个 yml。</p>
 */
class EvalRunnerTest {

    /** 规则通道技能召回率验收线：低于它说明别名字典覆盖不足 */
    private static final double MIN_SKILL_RECALL = 0.90;

    /** 规则通道技能精确率验收线：低于它说明字典/抽取误命中（假阳性贵，从严） */
    private static final double MIN_SKILL_PRECISION = 0.90;

    /** evidence 校验两个方向都要求零失误：合成集上这是确定性能力，不打折 */
    private static final double MIN_EVIDENCE_RETENTION = 1.00;
    private static final double MIN_HALLUCINATION_REJECTION = 1.00;

    /** 经历条目数一致率：5 例至少要中 4 例 */
    private static final double MIN_EXPERIENCE_ACCURACY = 0.80;

    private static SkillDictionary dict;
    private static Map<String, Object> report;

    @BeforeAll
    static void runEval() throws IOException {
        dict = SeedDictionaryTestSupport.fromSchemaSql();
        EvalRunner runner = new EvalRunner(dict, new ObjectMapper());
        // 测试类路径直接命中 src/test/resources/eval/golden.jsonl
        List<GoldenCase> cases = runner.loadGolden("classpath:eval/golden.jsonl");
        assertEquals(5, cases.size(), "金标准应包含 5 份合成简历");
        report = runner.run(cases, "v1-test");
    }

    @Test
    @DisplayName("聚合指标：规则通道精确率/召回率过阈值，evidence 校验零失误")
    @SuppressWarnings("unchecked")
    void aggregateMetricsMeetThresholds() {
        Map<String, Object> agg = (Map<String, Object>) report.get("aggregate");
        double recall = (double) agg.get("skillRecall");
        double precision = (double) agg.get("skillPrecision");
        double retention = (double) agg.get("evidenceRetentionRate");
        double rejection = (double) agg.get("hallucinationRejectionRate");
        double experience = (double) agg.get("experienceCountAccuracy");

        assertTrue(recall >= MIN_SKILL_RECALL,
                "规则通道召回率 " + recall + " 低于验收线 " + MIN_SKILL_RECALL + "，明细: " + report.get("cases"));
        assertTrue(precision >= MIN_SKILL_PRECISION,
                "规则通道精确率 " + precision + " 低于验收线 " + MIN_SKILL_PRECISION + "，明细: " + report.get("cases"));
        assertEquals(MIN_EVIDENCE_RETENTION, retention, 1e-9, "合法证据被校验器误杀");
        assertEquals(MIN_HALLUCINATION_REJECTION, rejection, 1e-9, "注入的幻觉技能未被全部拦截");
        assertTrue(experience >= MIN_EXPERIENCE_ACCURACY,
                "经历条目数一致率 " + experience + " 低于 " + MIN_EXPERIENCE_ACCURACY);
    }

    @Test
    @DisplayName("报告头部：版本与字典规模留痕（prompt_version 贯穿评测）")
    void reportCarriesProvenance() {
        assertEquals("v1-test", report.get("promptVersion"));
        assertTrue((int) report.get("aliasDictionarySize") >= 20);
        assertEquals(5, report.get("caseCount"));
        assertEquals("RULE-ONLY(deterministic)", report.get("channel"));
    }
}
