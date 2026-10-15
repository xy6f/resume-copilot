package com.resumecopilot.match;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 别名字典单测（规则通道的心脏）：种子数据完整性 + 三级归一判定 + 跨侧命中判据。
 */
class SkillDictionaryTest {

    private static List<String[]> seed;
    private static SkillDictionary dict;

    @BeforeAll
    static void loadSeed() throws IOException {
        seed = SeedDictionaryTestSupport.rowsFromSchemaSql();
        dict = new SkillDictionary(seed);
    }

    @Test
    @DisplayName("种子数据至少 20 条且解析自 schema.sql")
    void seedHasAtLeastTwentyRows() {
        assertTrue(seed.size() >= 20,
                "t_skill_alias 种子应不少于 20 条，实际 " + seed.size());
    }

    @Test
    @DisplayName("精确归一：JUC/Spring Data Redis/MybatisPlus 命中各自标准名")
    void exactAliasCanonicalization() {
        assertEquals("Java并发", dict.canonical("JUC"));
        assertEquals("Redis", dict.canonical("Spring Data Redis"));
        // 大小写与连字符差异在归一化里被抹平：MybatisPlus == MyBatis-Plus
        assertEquals("MyBatis", dict.canonical("MybatisPlus"));
        assertEquals("MyBatis", dict.canonical("mybatis plus"));
        // 标准名自身永远可查（字典自反）
        assertEquals("Kubernetes", dict.canonical("Kubernetes"));
    }

    @Test
    @DisplayName("包含式归一：写法含别名、别名含写法两个方向")
    void containmentCanonicalization() {
        // 方向1：写法里包含别名（"高并发编程实战" 含别名 "高并发编程"）
        assertEquals("Java并发", dict.canonical("高并发编程实战"));
        // 方向2：别名里包含写法（"java并发" 被别名 "Java并发编程" 包含）
        assertEquals("Java并发", dict.canonical("Java并发"));
        // 微服务组件 -> Spring Cloud（种子里的中文长别名）
        assertEquals("Spring Cloud", dict.canonical("微服务组件经验"));
    }

    @Test
    @DisplayName("未收录词保持自己（归一化形态），不做危险的全局模糊")
    void unknownStaysUnknown() {
        assertEquals("rust语言", dict.canonical("Rust 语言"));
        // 过短的词不参与包含式（防单字双字乱咬）
        assertEquals("并发", dict.canonical("并发"));
    }

    @Test
    @DisplayName("跨侧精确：JD 写法与简历写法归一到同一标准名即覆盖")
    void sameStandardAcrossSides() {
        assertTrue(dict.sameStandard("Java并发编程", "JUC"));
        assertTrue(dict.sameStandard("redis", "Spring Data Redis"));
        assertFalse(dict.sameStandard("Java", "Java并发"));
    }

    @Test
    @DisplayName("跨侧包含：Java 之于 Java并发 是弱关系（方向对、粒度不足）")
    void subsumingDetectsGranularityGap() {
        assertTrue(dict.subsuming("Java", "Java并发"));
        assertTrue(dict.subsuming("Spring", "Spring Cloud"));
        // 相等的两个标准名不是"包含关系"，它们是覆盖
        assertFalse(dict.subsuming("Redis", "Redis"));
        assertFalse(dict.subsuming("MySQL", "Redis"));
    }

    @Test
    @DisplayName("逐行抽取：一行里的多个别名各归其标准名且去重")
    void extractStandardsFromLine() {
        List<String> hits = dict.extractStandards(
                SkillDictionary.normalize("技能：Java、JUC、Spring Boot、MyBatis-Plus、Redis、MySQL"));
        assertTrue(hits.containsAll(List.of("Java", "Java并发", "Spring", "MyBatis", "Redis", "MySQL")),
                "六个别名应全部命中，实际 " + hits);
    }

    @Test
    @DisplayName("短别名不参与包含式匹配：JUC 不会被当成任意词的子串乱配")
    void shortAliasExcludedFromContainment() {
        // "juc" 长度 3 < MIN_CONTAIN_LEN：作为独立写法出现时（非标准别名行）
        // 只能靠精确键命中；这里构造一个包含 "juc" 但不含别名的长词验证不误归一
        assertEquals("jucxxxyyy", dict.canonical("jucxxxyyy"));
    }
}
