package com.resumecopilot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * JD 结构化模型（与 {@link ResumeProfile} 对称）：JD 原文 -> 要求条目数组。
 *
 * <p>同样双重身份：LLM 结构化输出目标（entity() 按此类生成 JSON Schema）
 * 与 t_jd.req_json 持久化形态。降级路径（计划书 7 章 POST /api/jds）里，
 * 用户也可以直接按此类结构提交 JSON 绕过 LLM——Schema 即接口契约。</p>
 */
@Data
public class JdRequirements {

    /** 要求条目：一条"技能/年限/素质"诉求 = 匹配引擎的一个被考察单元 */
    private List<Requirement> requirements = new ArrayList<>();

    @Data
    public static class Requirement {
        /** 技能名（尽量短，供别名归一）：如"Java并发""MySQL" */
        private String skill;
        /** JD 原文里这条要求的表述（进报告证据区，用户看得懂的话） */
        private String description;
        /** 这条要求隐含的年限（年），没有写 0：年限维度的输入 */
        private int minYears;
    }
}
