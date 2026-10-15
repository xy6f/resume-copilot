package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** JD 记录（t_jd，计划书 6 章）：原文与结构化要求条目并存的唯一落点 */
@Data
@TableName("t_jd")
public class JdRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 岗位名，可为空串（LLM 未配置且用户只贴了正文时） */
    private String title;

    /** 公司名，同上可为空串 */
    private String company;

    /** JD 原文：和简历原文同级别的"事实源"，要求条目可疑时可凭它重解析 */
    private String rawText;

    /** JdRequirements 的 JSON 序列化结果（要求条目数组） */
    private String reqJson;

    /** 录入渠道：TEXT=文本粘贴（当前唯一），URL 预留 */
    private String source;

    private LocalDateTime createdAt;
}
