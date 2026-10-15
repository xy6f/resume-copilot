package com.resumecopilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 匹配打分配置（application.yml 的 resume-copilot.match.*）。
 *
 * <p>计划书 5.3"权重可配置"：五维权重集中在这里，改口径=改配置不改代码；
 * 每次报告的 detail_json 里会把当次权重整组留痕，分数争议可复盘。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "resume-copilot.match")
public class MatchProperties {

    /** 技能覆盖维权重 */
    private double weightSkill = 0.35;

    /** 经历相关性维权重 */
    private double weightExperience = 0.25;

    /** 年限匹配维权重 */
    private double weightYears = 0.15;

    /** 项目深度维权重 */
    private double weightDepth = 0.15;

    /** 表达质量维权重 */
    private double weightExpression = 0.10;

    /**
     * 语义通道余弦相似度阈值：低于它近邻词会虚增覆盖（假阳性比漏报更难解释，宁高勿低）。
     * 计划书 5.2：相似度大于阈值判"弱相关"，不直接算覆盖——语义漂移是已知坑。
     */
    private double semanticThreshold = 0.85;

    /** 一次 SSE 最多为几条低分项生成建议（token 成本护栏，计划书非功能 3.2） */
    private int adviceMaxItems = 3;
}
