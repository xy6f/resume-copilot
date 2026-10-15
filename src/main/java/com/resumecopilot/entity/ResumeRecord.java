package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 简历记录（t_resume）：原文/结构化结果/成本留痕（计划书 6 章） */
@Data
@TableName("t_resume")
public class ResumeRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传文件名（本地存储路径由 id 派生，不入库减少脏字段） */
    private String fileName;

    /** PDF 抽出的原文（后续分块抽取、evidence 校验都要用它） */
    private String rawText;

    /** ResumeProfile 的 JSON 序列化结果 */
    private String profileJson;

    /** 抽取用的 prompt 版本：分数/结果必须可追溯到"哪版 prompt 哪版模型" */
    private String promptVersion;

    private String model;

    /** 本次 LLM 调用 token 成本（计划书非功能：成本意识） */
    private Integer tokenCost;

    private LocalDateTime createdAt;
}
