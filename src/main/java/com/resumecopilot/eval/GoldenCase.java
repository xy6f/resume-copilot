package com.resumecopilot.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 金标准评测用例（golden.jsonl 一行的反序列化形态，计划书 5.4）。
 *
 * <p>expectedSkills 用"标准技能名"口径（字典列，不是原文写法）：金标准标注的
 * 是语义事实（这份简历体现的技能），写法归一是被测系统的内部事——把期望锚在
 * 标准名上，改别名表不会把评测基准一起改废。expectedBulletCount 同理：
 * 标注的是经历条目数这一人工可读事实。</p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoldenCase {

    /** 用例编号：落 t_eval_case.case_key，报告里也回显，badcase 定位用 */
    private String id;

    /** 合成简历文本（本项目为脱敏模拟数据；真实评测集替换内容、格式不变） */
    private String resumeText;

    /** 人工标注：该简历体现的标准技能名全集 */
    private List<String> expectedSkills = new ArrayList<>();

    /** 人工标注：经历 bullet 条数 */
    private int expectedBulletCount;

    /** 标注备注：埋了哪些坑（如同义写法、缩写、干扰词） */
    private String note = "";
}
