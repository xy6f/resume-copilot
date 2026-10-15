package com.resumecopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.entity.ResumeRecord;
import com.resumecopilot.extract.ResumeExtractionService;
import com.resumecopilot.mapper.ResumeRecordMapper;
import com.resumecopilot.match.EvidenceValidator;
import com.resumecopilot.match.LexicalResumeExtractor;
import com.resumecopilot.match.SkillDictionaryService;
import com.resumecopilot.model.ResumeProfile;
import com.resumecopilot.parse.PdfTextExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 上传 -> 解析 -> 抽取 -> 落库 的编排服务（计划书 4 章 parse/extract 链）。
 *
 * <p>W1 同步执行（简历一份 10 秒内可接受）；计划书异步化（轮询/推送）留 W2，
 * 这里注释明确边界而不是提前过度设计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeIngestService {

    private final PdfTextExtractor pdfTextExtractor;
    private final ResumeExtractionService extractionService;
    private final ResumeRecordMapper resumeMapper;
    private final SkillDictionaryService dictionaryService;
    private final ObjectMapper objectMapper;

    @Value("${resume-copilot.upload-dir}")
    private String uploadDir;

    @Value("${resume-copilot.prompt-version}")
    private String promptVersion;

    public Long ingest(MultipartFile file) throws IOException {
        // 1. 存原始 PDF：原文是唯一事实源，任何重抽取都从它来（可复现性）
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        Path stored = dir.resolve(UUID.randomUUID() + ".pdf");
        file.transferTo(stored.toFile());

        // 2. 抽文本
        String text = pdfTextExtractor.extract(stored.toFile());

        // 3. LLM 结构化抽取
        ResumeProfile profile = extractionService.extract(text);

        // 3.5 幻觉后置校验：prompt 里"必须带证据"是君子协定，这里是合同——
        //     evidence 不是原文子串的技能直接丢弃（丢弃数记日志，全量留痕在匹配阶段）
        List<String> discarded = EvidenceValidator.sanitize(profile, text);
        if (!discarded.isEmpty()) {
            log.warn("resume {}: {} skills discarded by evidence validation {}",
                    file.getOriginalFilename(), discarded.size(), discarded);
        }

        // 4. 落库：profile_json 序列化失败=数据异常直接抛，不做半条脏记录
        ResumeRecord r = new ResumeRecord();
        r.setFileName(file.getOriginalFilename());
        r.setRawText(text);
        r.setProfileJson(objectMapper.writeValueAsString(profile));
        r.setPromptVersion(promptVersion);
        r.setModel("configured");   // W2 起由响应元数据回填真实模型名
        resumeMapper.insert(r);
        log.info("resume {} ingested, {} chars parsed", r.getId(), text.length());
        return r.getId();
    }

    /**
     * 降级摄取（无 LLM key 的简历录入路径，计划书 9 章"明确边界"的落点之一）：
     * 提交简历纯文本；不带 profile 时用别名字典做 lexical 抽取（确定性规则通道，
     * 质量低于 LLM 抽取但证据天然合法——evidence 即行原文）。
     * 返回 {resumeId, degraded, discardedSkills}，degraded=true 表示没走模型。
     */
    public Map<String, Object> ingestRaw(String fileName, String rawText, ResumeProfile submitted)
            throws IOException {
        ResumeProfile profile;
        boolean degraded;
        if (submitted != null) {
            // 用户直接提交结构化画像：只过证据闸，不替用户改内容——校正回流场景下
            // "用户坚持写简历上没有的技能"应当被闸拦下并如实返回 discardedSkills
            profile = submitted;
            degraded = false;
        } else {
            profile = LexicalResumeExtractor.extract(rawText, dictionaryService.load());
            degraded = true;
        }
        List<String> discarded = EvidenceValidator.sanitize(profile, rawText);

        ResumeRecord r = new ResumeRecord();
        r.setFileName(fileName == null || fileName.isBlank() ? "manual.txt" : fileName);
        r.setRawText(rawText);
        r.setProfileJson(objectMapper.writeValueAsString(profile));
        // 版本口径：降级抽取与 prompt 无关，后缀 -LEX 明示出身，评测/复盘不混账
        r.setPromptVersion(promptVersion + (degraded ? "-LEX" : ""));
        r.setModel(degraded ? "lexical-rule-channel" : "user-submitted");
        resumeMapper.insert(r);
        log.info("raw resume {} ingested degraded={} discarded={}", r.getId(), degraded, discarded);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resumeId", r.getId());
        out.put("degraded", degraded);
        out.put("discardedSkills", discarded);
        return out;
    }

    /**
     * 校正回流（计划书 7 章 #3）：整体替换 profile_json，替换前照跑证据校验。
     * 返回实际丢弃的技能——校正也要吃"证据必须来自原文"这条纪律，不存在特权写入。
     */
    public Map<String, Object> updateProfile(Long id, ResumeProfile profile) throws IOException {
        ResumeRecord r = resumeMapper.selectById(id);
        if (r == null) {
            // 根因修复（测试发现的同类缺陷）：原实现把"查无此简历"抛成 IOException，
            // 全局 advice 没有该映射，落到 Spring 默认处理返回 500——语义应是 404。
            // 统一改抛 NotFoundException，由 ApiExceptionAdvice 收口
            throw new NotFoundException("简历记录不存在: " + id);
        }
        List<String> discarded = EvidenceValidator.sanitize(profile, r.getRawText());
        r.setProfileJson(objectMapper.writeValueAsString(profile));
        r.setModel("user-corrected");
        resumeMapper.updateById(r);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", true);
        out.put("discardedSkills", discarded);
        return out;
    }

    public ResumeProfile getProfile(Long id) throws IOException {
        ResumeRecord r = resumeMapper.selectById(id);
        if (r == null) {
            // 根因修复（README 已知待办）：GET /api/resumes/{id} 对不存在 id 此前 500，
            // 现统一抛 NotFoundException → 全局 advice 404 + 友好错误体
            throw new NotFoundException("简历记录不存在: " + id);
        }
        return objectMapper.readValue(r.getProfileJson(), ResumeProfile.class);
    }
}
