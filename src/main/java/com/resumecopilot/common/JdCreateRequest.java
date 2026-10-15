package com.resumecopilot.common;

import com.resumecopilot.model.JdRequirements;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * POST /api/jds 请求体（计划书 7 章 #4）。
 *
 * <p>两条路径共用一个接口形态：
 * 1. 只给 rawText——服务端 LLM 解析出 requirements 后落库；
 * 2. rawText + requirements——直接提交已结构化条目（LLM 不可用时的官方降级路径，
 *    也用于"用户手工修正解析结果再提交"的校正回流场景）。
 * requirements 一旦给出就完全绕过 LLM：解析是手段，结构化条目才是目的。</p>
 */
@Data
public class JdCreateRequest {

    /** 岗位名，选填 */
    private String title;

    /** 公司名，选填 */
    private String company;

    /** JD 原文，必填：原文是事实源，结构化条目只是派生物 */
    @NotBlank(message = "rawText 不能为空")
    private String rawText;

    /** 录入渠道：默认 TEXT */
    private String source;

    /** 已结构化的要求条目；提供则跳过 LLM 解析直接入库（降级/校正路径） */
    private List<JdRequirements.Requirement> requirements;
}
