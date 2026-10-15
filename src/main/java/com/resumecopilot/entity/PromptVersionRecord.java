package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Prompt 版本（t_prompt_version，计划书 5.4/6 章）：结果必须能追溯"哪版 prompt 产出的" */
@Data
@TableName("t_prompt_version")
public class PromptVersionRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场景：EXTRACT / JD_PARSE / ADVICE */
    private String scene;

    /** 版本号，与配置 resume-copilot.prompt-version 对应 */
    private String version;

    /** prompt 全文留档（当前种子行先记"正文在代码常量"的出处指认） */
    private String content;

    /** 版本叙事：这版为什么这么改 */
    private String note;

    private LocalDateTime createdAt;
}
