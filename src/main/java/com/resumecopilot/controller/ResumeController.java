package com.resumecopilot.controller;

import com.resumecopilot.model.ResumeProfile;
import com.resumecopilot.service.ResumeHistoryService;
import com.resumecopilot.service.ResumeIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 简历接口（计划书第 7 章 #1 #2 #3）。
 *
 * <p>上传 PDF 走 LLM 抽取（未配 key 时 503+指引）；/raw 与 PUT profile
 * 是两条不依赖模型的口子：前者供纯文本降级摄取（规则通道 lexical 抽取），
 * 后者供用户校正抽取结果（校正数据同时是 few-shot 回流的原料）。
 * 三条路共用同一条证据纪律：落库前都过 EvidenceValidator。</p>
 */
@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeIngestService ingestService;
    private final ResumeHistoryService historyService;

    /** 上传 PDF，同步返回 resumeId（异步化在 W2，接口形态不变只改响应码 202） */
    @PostMapping
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
        Long id = ingestService.ingest(file);
        return ResponseEntity.ok(Map.of("resumeId", id));
    }

    /** 降级摄取：提交简历纯文本，无 LLM 也可用（规则通道抽取或直接提交结构化画像） */
    @PostMapping("/raw")
    public ResponseEntity<Map<String, Object>> uploadRaw(@Valid @RequestBody RawResumeRequest req)
            throws IOException {
        return ResponseEntity.ok(ingestService.ingestRaw(
                req.getFileName(), req.getRawText(), req.getProfile()));
    }

    /** 取结构化简历 */
    @GetMapping("/{id}")
    public ResponseEntity<ResumeProfile> profile(@PathVariable Long id) throws IOException {
        return ResponseEntity.ok(ingestService.getProfile(id));
    }

    /** 用户校正抽取结果（计划书 7 章 #3）：整体替换 profile_json，返回被证据闸丢弃的技能 */
    @PutMapping("/{id}/profile")
    public ResponseEntity<Map<String, Object>> correct(@PathVariable Long id,
                                                       @RequestBody ResumeProfile profile)
            throws IOException {
        return ResponseEntity.ok(ingestService.updateProfile(id, profile));
    }

    /**
     * 历史版本对比（W4 遗留）：同一 rawText 指纹（SHA-256）最近 5 次摄取的
     * id/时间/最新报告总分列表，前端画分数曲线用。
     * 口径与设计取舍见 ResumeHistoryService 类注释；查无此简历 404 收口照旧。
     */
    @GetMapping("/{id}/history")
    public ResponseEntity<Map<String, Object>> history(@PathVariable Long id) {
        return ResponseEntity.ok(historyService.history(id));
    }
}
