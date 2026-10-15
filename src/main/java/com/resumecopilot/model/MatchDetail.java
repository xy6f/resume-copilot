package com.resumecopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 匹配报告明细（t_match_report.detail_json 的结构）：计划书 3.1"技能覆盖矩阵 +
 * 五维打分 + 总分评级"的落点。整份可 JSON 化，报告详情接口原样吐出。
 */
@Data
public class MatchDetail {

    /** 简历 id x JD id：报告坐标 */
    private Long resumeId;
    private Long jdId;

    /** 出身三元组：哪版 prompt、哪个模型声明、字典条数（规则通道可复现性的最小描述） */
    private String promptVersion;
    private String model;
    private int aliasDictionarySize;

    /** 语义通道状态：false 时本字段为 false 且 semanticNote 必非空——降级要写进报告，不能静默 */
    private boolean semanticChannelEnabled;
    private String semanticNote;

    /** 幻觉后置校验：被丢弃的"无有效证据"技能名（计划书 5.3 防线的运行痕迹） */
    private List<String> discardedSkills = new ArrayList<>();

    /** 覆盖矩阵：逐条 JD 要求的判定结果 */
    private List<RequirementOutcome> outcomes = new ArrayList<>();

    /** 五维得分（各 0~100） */
    private Dimensions dimensions = new Dimensions();

    /** 打分权重：报告里留痕权重，分数争议时可复盘是不是口径问题 */
    private Weights weights = new Weights();

    /** 加权总分与评级 */
    private int totalScore;
    private String grade;

    /** 一条 JD 要求的判定结果 */
    @Data
    public static class RequirementOutcome {
        private String skill;          // 归一后的标准技能名
        private String rawSkill;       // JD 里的原始写法
        private String description;    // JD 原文表述
        private int minYears;
        /** COVERED 覆盖 / WEAK 弱覆盖 / MISSING 缺失（计划书 5.2 三态） */
        private String status;
        /** 命中通道：RULE / SEMANTIC / NONE——弱覆盖是规则还是语义给的，人工要能一眼分辨 */
        private String channel;
        /** 证据：命中的简历技能名 + 其原文 evidence（缺失项为空列表） */
        private List<String> evidences = new ArrayList<>();
        /** 语义通道相似度（未走语义通道时为 null，区分"没算"和"算出 0"） */
        private Double similarity;
    }

    /** 五维得分 */
    @Data
    public static class Dimensions {
        private int skillCoverage;       // 技能覆盖
        private int experienceRelevance; // 经历相关性
        private int yearsFit;            // 年限匹配
        private int projectDepth;        // 项目深度
        private int expressionQuality;   // 表达质量
    }

    /** 五维权重 */
    @Data
    public static class Weights {
        private double skill = 0.35;
        private double experience = 0.25;
        private double years = 0.15;
        private double depth = 0.15;
        private double expression = 0.10;
    }
}
