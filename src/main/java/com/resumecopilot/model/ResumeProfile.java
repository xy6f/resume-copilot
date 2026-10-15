package com.resumecopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 简历结构化模型（计划书 5.1 的 Schema 落成 Java 类）。
 *
 * <p>双重身份（设计点）：
 * 1. Spring AI 结构化输出目标——.entity(ResumeProfile.class) 时框架按此类生成
 *    JSON Schema 约束模型输出，字段注释即提示词的一部分；
 * 2. 持久化模型——序列化 JSON 存 t_resume.profile_json。
 * 保持"抽取 Schema 即存储 Schema"避免多一层映射的漂移风险，代价是演进要加字段，
 * 属可接受取舍（面试可讲这层权衡）。</p>
 */
@Data
public class ResumeProfile {

    /** 基本信息 */
    public Basic basic = new Basic();
    /** 技能列表：name+level+evidence（evidence 必填原文片段=幻觉防线，计划书 5.3） */
    public List<Skill> skills = new ArrayList<>();
    /** 实习/项目/竞赛等条目化经历 */
    public List<Experience> experiences = new ArrayList<>();

    @Data
    public static class Basic {
        public String name;
        public String grade;      // 如 2027届
        public String school;
        public String major;
        public String gpa;
    }

    @Data
    public static class Skill {
        public String name;
        public String level;       // 了解/熟悉/掌握/精通——对齐 JD 用词后归一化
        public String evidence;    // 支撑该技能的简历原文；无证据的技能丢弃
    }

    @Data
    public static class Experience {
        public String type;        // 实习/项目/竞赛/社团
        public String title;
        public String org;
        public String period;
        public List<String> bullets = new ArrayList<>();
        /** 从 bullet 里识别出的量化数据（"QPS 提升 x%"），改写建议的证据源 */
        public List<String> highlights = new ArrayList<>();
    }
}
