package com.resumecopilot.match;

import com.resumecopilot.model.ResumeProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幻觉后置校验单测（计划书 5.3 防线，任务点名的重点对象）：
 * evidence 必须是 rawText 空白归一化后的子串，不合格一律丢弃。
 */
class EvidenceValidatorTest {

    private static final String RAW = """
            技能：Java、JUC、Redis
            项目经历
            - 基于Spring Boot实现订单模块，接口耗时下降40%
            """;

    @Test
    @DisplayName("合法证据：跨换行/空格差异仍然合格（空白归一化的意义）")
    void whitespaceTolerantMatch() {
        // 原文里是 "Spring Boot"，evidence 抄成 "SpringBoot"——排版噪声差异，放行
        assertTrue(EvidenceValidator.isValidEvidence(RAW, "基于SpringBoot实现订单模块"));
        // 抄成多空格形态同样放行
        assertTrue(EvidenceValidator.isValidEvidence(RAW, "  基于 Spring   Boot 实现订单模块 "));
        assertTrue(EvidenceValidator.isValidEvidence(RAW, "技能：Java、JUC、Redis"));
    }

    @Test
    @DisplayName("全角空格也被归一：PDF 抽取常见 U+3000 不该造成误杀")
    void fullWidthSpaceNormalized() {
        String rawWithWide = "熟悉 Java　与　Redis 缓存";
        assertTrue(EvidenceValidator.isValidEvidence(rawWithWide, "Java与Redis缓存"));
    }

    @Test
    @DisplayName("幻觉证据：原文没有的内容，改一个词也必须拦下")
    void fabricatedEvidenceRejected() {
        // 原文是 "订单模块"，LLM 幻觉成 "支付模块"——子串关系断，拦
        assertFalse(EvidenceValidator.isValidEvidence(RAW, "基于Spring Boot实现支付模块，接口耗时下降40%"));
        // 无中生有的量化句
        assertFalse(EvidenceValidator.isValidEvidence(RAW, "主导千万级流量系统架构设计"));
        // 大小写差异不放行：刻意严格的口径（README 有说明），宁可用户补写
        assertFalse(EvidenceValidator.isValidEvidence(RAW, "JAVa"));
    }

    @Test
    @DisplayName("空证据与空原文：空 evidence 一律不合格；没有原文就没有事实源")
    void blankInputsRejected() {
        assertFalse(EvidenceValidator.isValidEvidence(RAW, "   "));
        assertFalse(EvidenceValidator.isValidEvidence(RAW, null));
        assertFalse(EvidenceValidator.isValidEvidence(null, "技能：Java"));
        assertFalse(EvidenceValidator.isValidEvidence("", "技能：Java"));
    }

    @Test
    @DisplayName("sanitize：真技能全留、假技能丢弃并回报名单")
    void sanitizeKeepsValidDiscardsInvalid() {
        ResumeProfile profile = new ResumeProfile();
        profile.getSkills().add(skill("Java", "技能：Java、JUC、Redis"));
        profile.getSkills().add(skill("Go", "精通Go语言三年"));        // 幻觉：原文无此句
        profile.getSkills().add(skill("MySQL", ""));                  // 无证据
        profile.getSkills().add(skill("Kafka", "使用Kafka构建日志管道")); // 幻觉：整句编造

        List<String> discarded = EvidenceValidator.sanitize(profile, RAW);

        assertEquals(List.of("Go", "MySQL", "Kafka"), discarded);
        assertEquals(1, profile.getSkills().size());
        assertEquals("Java", profile.getSkills().get(0).getName());
    }

    private static ResumeProfile.Skill skill(String name, String evidence) {
        ResumeProfile.Skill s = new ResumeProfile.Skill();
        s.setName(name);
        s.setEvidence(evidence);
        return s;
    }
}
