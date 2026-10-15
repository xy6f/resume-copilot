package com.resumecopilot.controller;

import com.resumecopilot.model.ResumeProfile;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * POST /api/resumes/raw 请求体（降级/校正摄取，无 LLM key 的简历录入口子）。
 *
 * <p>rawText 必填（原文永远是事实源）；profile 选填——不给就走别名字典的
 * 规则通道 lexical 抽取，给了就直接采用（仍过证据校验）。两条路都不碰模型。</p>
 */
@Data
public class RawResumeRequest {

    /** 展示用文件名，可空 */
    private String fileName;

    /** 简历纯文本（PDF 抽取产物或手贴文本均可） */
    @NotBlank(message = "rawText 不能为空")
    private String rawText;

    /** 已结构化的画像；提供则跳过降级抽取直接采用 */
    private ResumeProfile profile;
}
