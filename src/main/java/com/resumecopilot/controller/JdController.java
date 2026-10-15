package com.resumecopilot.controller;

import com.resumecopilot.entity.JdRecord;
import com.resumecopilot.common.JdCreateRequest;
import com.resumecopilot.jd.JdIngestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * JD 接口（计划书第 7 章 #4 POST /api/jds）。
 * 只收 JSON 文本录入；multipart/URL 抓取不在 MVP 范围（明确边界，计划书 9 章原则）。
 */
@RestController
@RequestMapping("/api/jds")
@RequiredArgsConstructor
public class JdController {

    private final JdIngestService jdIngestService;

    /**
     * 录入 JD：body 带 requirements 走降级/校正路径（无 LLM 也可用）；
     * 不带则 LLM 解析——未配置 key 时返回 503 + hint 指引走前一条路径。
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@Valid @RequestBody JdCreateRequest request) {
        return ResponseEntity.ok(jdIngestService.create(request));
    }

    /** 取 JD 原始记录（确认解析结果用） */
    @org.springframework.web.bind.annotation.GetMapping("/{id}")
    public ResponseEntity<com.resumecopilot.entity.JdRecord> get(@PathVariable Long id) {
        return ResponseEntity.ok(jdIngestService.requireJd(id));
    }
}
