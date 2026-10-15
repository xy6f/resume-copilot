package com.resumecopilot.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 匹配报告（t_match_report，计划书 6 章）：一次"简历 x JD"打分的全量留痕 */
@Data
@TableName("t_match_report")
public class MatchReportRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long resumeId;

    private Long jdId;

    /** 五维加权总分（0~100 取整） */
    private Integer totalScore;

    /** 评级：优秀/良好/一般/较差 */
    private String grade;

    /** MatchDetail 序列化：覆盖矩阵+五维分+权重+语义通道状态，报告详情的数据源 */
    private String detailJson;

    /** 打分时的模型声明：当前引擎纯规则+可选本地化 embedding，如实记录配置值 */
    private String model;

    /** 匹配环节 token 成本：纯规则通道为 0，语义通道启用后可回填 */
    private Integer tokenCost;

    /** prompt/字典版本出身：可复现三元组（简历版本 x 字典 x prompt 版本）之一 */
    private String promptVersion;

    private LocalDateTime createdAt;
}
