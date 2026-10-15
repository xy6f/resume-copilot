package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 改写建议（t_rewrite_advice，计划书 6 章）：SSE 流式生成完成后逐条落库 */
@Data
@TableName("t_rewrite_advice")
public class RewriteAdvice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 归属报告 */
    private Long reportId;

    /** 针对的 JD 低分项（要求条目原文），即"改哪一段要对准谁" */
    private String targetBullet;

    /** 简历原句/证据（缺失项可能没有原句，允许空） */
    private String originalText;

    /** LLM 生成的改写建议全文 */
    private String suggestedText;

    /** 改动的考察点依据（计划书 5.3：每条建议必须附依据，面试可讲） */
    private String reason;

    /** PENDING/ACCEPTED/IGNORED——采纳率是产品指标，先埋点 */
    private String status;

    private LocalDateTime createdAt;
}
