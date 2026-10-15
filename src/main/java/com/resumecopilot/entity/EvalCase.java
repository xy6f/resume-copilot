package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评测标注用例（t_eval_case，计划书 6 章）：golden.jsonl 的落库形态，供批量导入/版本对比 */
@Data
@TableName("t_eval_case")
public class EvalCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用例编号，与 golden.jsonl 的 id 一致 */
    private String caseKey;

    /** 标注用简历文本（本项目当前为合成脱敏模拟数据） */
    private String resumeText;

    /** 人工金标准 JSON：期望技能标准名列表等 */
    private String expectedJson;

    /** 标注备注（埋了哪些坑） */
    private String note;

    private LocalDateTime createdAt;
}
