package com.resumecopilot.controller;

import com.resumecopilot.advise.AdviceService;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.model.MatchDetail;
import com.resumecopilot.service.MatchService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 匹配接口（计划书 7 章 #5 #6 #7）：
 * POST /api/match 生成报告；GET /api/match/{id} 看明细；GET .../advice 流式改写建议。
 *
 * <p>报告生成是纯规则通道（可选语义通道），不依赖对话模型——没有 OPENAI_API_KEY
 * 也能出完整报告；只有 advice 一个口子需要真实 LLM，未配置时它在推流开始前
 * 就抛 503（见 AdviceService 注释），错误体走全局 JSON 约定。</p>
 */
@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;
    private final AdviceService adviceService;

    /** 请求体：{resumeId, jdId}，两个都是已有记录主键 */
    @Data
    public static class MatchRequest {
        private Long resumeId;
        private Long jdId;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody MatchRequest req) {
        if (req.getResumeId() == null || req.getJdId() == null) {
            throw new IllegalArgumentException("resumeId 与 jdId 必填");
        }
        MatchReportRecord record = matchService.createMatch(req.getResumeId(), req.getJdId());
        MatchDetail detail = matchService.readDetail(record);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportId", record.getId());
        out.put("totalScore", record.getTotalScore());
        out.put("grade", record.getGrade());
        out.put("semanticChannelEnabled", detail.isSemanticChannelEnabled());
        out.put("discardedSkills", detail.getDiscardedSkills());
        return ResponseEntity.ok(out);
    }

    /** 报告详情：totalScore/grade + 完整 detail_json 反序列化形态 */
    @GetMapping("/{reportId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long reportId) {
        MatchReportRecord record = matchService.requireReport(reportId);
        MatchDetail detail = matchService.readDetail(record);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("reportId", record.getId());
        out.put("createdAt", record.getCreatedAt());
        out.put("totalScore", record.getTotalScore());
        out.put("grade", record.getGrade());
        out.put("detail", detail);
        return ResponseEntity.ok(out);
    }

    /**
     * SSE 改写建议（计划书 7 章 #7）：event=advice_chunk 逐 token 推正文，
     * event=advice_item_done 一条低分项建议完成（data 含落库 id），event=done 收尾。
     * 判定顺序：报告不存在先于 LLM 可用性门——查无此报告一律 404，
     * 只有报告真实存在且未配置 key 时 AdviceService 才在进入流式前抛
     * LlmUnavailableException，由全局 advice 映射 503——此时尚未占用流式
     * 响应，错误体仍是标准 JSON。
     */
    @GetMapping(value = "/{reportId}/advice", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> advice(@PathVariable Long reportId) {
        return adviceService.streamAdvice(reportId);
    }
}
