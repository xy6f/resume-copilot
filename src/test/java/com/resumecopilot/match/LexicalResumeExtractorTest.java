package com.resumecopilot.match;

import com.resumecopilot.model.ResumeProfile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则通道抽取的经历分段单测（缺陷修复配套）：
 * 修复前多段经历（实习经历 + 项目经历）会全部塌进一张
 * title="规则通道降级抽取" 的占位卡；修复后每个经历标题独立成一段，
 * title 用真实标题，bullets/highlights 归各自段。
 */
class LexicalResumeExtractorTest {

    private static SkillDictionary dict;

    @BeforeAll
    static void loadDict() throws Exception {
        // 与评测同一份种子字典：抽取技能口径与 /raw 接口一致
        dict = SeedDictionaryTestSupport.fromSchemaSql();
    }

    /** 双段经历简历：项目经历 2 条 bullet（其中 1 条含量化数据），实习经历 2 条 bullet */
    private static final String DUAL_SECTION_RESUME = """
            姓名：李雷 学校：XX大学 软件工程 2027届
            技能：Java、Spring Boot、Redis、MySQL
            项目经历
            - 基于Spring Boot与MyBatis-Plus实现订单模块，接口平均耗时下降40%
            - 用本地缓存与批处理优化报表导出，不追求具体数字的表述
            实习经历
            - 在XX公司后端组参与订单域开发6个月
            - 使用Redis缓存热点数据，QPS从800提升到3200
            """;

    @Test
    @DisplayName("双段简历出两张卡：每段独立成 Experience，title 为真实标题")
    void dualSectionResumeYieldsTwoExperienceCards() {
        ResumeProfile profile = LexicalResumeExtractor.extract(DUAL_SECTION_RESUME, dict);

        assertEquals(2, profile.getExperiences().size(), "双段经历应产出两张经历卡");

        ResumeProfile.Experience project = profile.getExperiences().get(0);
        assertEquals("项目经历", project.getTitle());
        assertEquals("项目", project.getType());
        assertEquals(2, project.getBullets().size());
        // highlights 归各自段：只有含数字的那条进量化点
        assertEquals(1, project.getHighlights().size());
        assertTrue(project.getHighlights().get(0).contains("40%"));

        ResumeProfile.Experience intern = profile.getExperiences().get(1);
        assertEquals("实习经历", intern.getTitle());
        assertEquals("实习", intern.getType());
        assertEquals(2, intern.getBullets().size());
        // 两条 bullet 都含数字（6个月 / QPS 800→3200），都进量化点
        assertEquals(2, intern.getHighlights().size());
        assertTrue(intern.getHighlights().stream().anyMatch(h -> h.contains("QPS")));

        // 跨段不串味：实习段的 bullet 不会混进项目段
        assertTrue(project.getBullets().stream().noneMatch(b -> b.contains("XX公司")));
    }

    @Test
    @DisplayName("英文标题等价识别：Internship Experience / Projects 各成一段")
    void englishHeadingsAreRecognizedAsSections() {
        String resume = """
                Skills: Java, Docker, Kubernetes
                Internship Experience
                - Backend intern at ACME, cut P99 latency by 35%
                Projects
                - Built a crawler handling 100k pages per day
                """;
        ResumeProfile profile = LexicalResumeExtractor.extract(resume, dict);

        assertEquals(2, profile.getExperiences().size());
        assertEquals("Internship Experience", profile.getExperiences().get(0).getTitle());
        assertEquals("实习", profile.getExperiences().get(0).getType());
        assertEquals("Projects", profile.getExperiences().get(1).getTitle());
        assertEquals("项目", profile.getExperiences().get(1).getType());
    }

    @Test
    @DisplayName("竞赛/社团标题同样开段；bullet 行里带关键词不误开新段")
    void clubAndContestSectionsAndBulletGuard() {
        String resume = """
                技能：Java
                竞赛经历
                - ACM 区域赛银奖
                社团活动
                - 参与社团项目并负责招新 30 人
                """;
        ResumeProfile profile = LexicalResumeExtractor.extract(resume, dict);

        assertEquals(2, profile.getExperiences().size());
        assertEquals("竞赛经历", profile.getExperiences().get(0).getTitle());
        assertEquals("竞赛", profile.getExperiences().get(0).getType());
        // "- 参与社团项目..." 是 bullet：归入社团段，不会因含"项目"误开第三段
        assertEquals("社团活动", profile.getExperiences().get(1).getTitle());
        assertEquals(1, profile.getExperiences().get(1).getBullets().size());
    }

    @Test
    @DisplayName("单段简历行为不回退：一张卡、真实标题")
    void singleSectionResumeStillOneCard() {
        String resume = """
                技能：Java、MySQL
                项目经历
                - 订单模块耗时下降40%
                """;
        ResumeProfile profile = LexicalResumeExtractor.extract(resume, dict);
        assertEquals(1, profile.getExperiences().size());
        assertEquals("项目经历", profile.getExperiences().get(0).getTitle());
    }
}
