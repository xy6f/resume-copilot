package com.resumecopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.extract.ResumeExtractionService;
import com.resumecopilot.jd.JdIngestService;
import com.resumecopilot.jd.JdParsingService;
import com.resumecopilot.mapper.JdRecordMapper;
import com.resumecopilot.mapper.MatchReportMapper;
import com.resumecopilot.mapper.ResumeRecordMapper;
import com.resumecopilot.match.ScoreEngine;
import com.resumecopilot.match.SkillDictionaryService;
import com.resumecopilot.model.ResumeProfile;
import com.resumecopilot.parse.PdfTextExtractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "查无此资源"统一抛 NotFoundException 的服务层契约测试（缺陷修复配套）：
 * GET /api/resumes/{id}、PUT /api/resumes/{id}/profile、GET /api/jds/{id}、
 * GET /api/match/{id}、POST /api/match —— 五条路径此前分别以 500（IOException 未映射）
 * 或 400（IllegalArgumentException 被当入参错误）泄出，现统一收口 404。
 * 状态码映射本身由 ApiExceptionAdviceTest 覆盖，这里钉住"服务层抛对了类型"。
 */
class ResourceNotFoundContractTest {

    private ResumeIngestService resumeService() {
        ResumeRecordMapper mapper = mock(ResumeRecordMapper.class);
        when(mapper.selectById(any())).thenReturn(null);
        return new ResumeIngestService(mock(PdfTextExtractor.class),
                mock(ResumeExtractionService.class), mapper,
                mock(SkillDictionaryService.class), new ObjectMapper());
    }

    private JdIngestService jdService() {
        JdRecordMapper mapper = mock(JdRecordMapper.class);
        when(mapper.selectById(any())).thenReturn(null);
        return new JdIngestService(mock(JdParsingService.class), mapper, new ObjectMapper());
    }

    private MatchService matchService() {
        ResumeRecordMapper resumeMapper = mock(ResumeRecordMapper.class);
        MatchReportMapper reportMapper = mock(MatchReportMapper.class);
        when(resumeMapper.selectById(any())).thenReturn(null);
        when(reportMapper.selectById(any())).thenReturn(null);
        return new MatchService(resumeMapper, reportMapper, jdService(),
                mock(SkillDictionaryService.class),
                new ScoreEngine(null, 0.85), new MatchProperties(), new ObjectMapper(),
                // LLM 门控与"查无资源"契约无关：给一个恒可用的真身，让 404 路径纯净
                new LlmAvailability("sk-unit-test-real-key"));
    }

    @Test
    @DisplayName("GET /api/resumes/{id} 查无记录：抛 NotFoundException（修复前是 IOException→500）")
    void getProfileMissingThrowsNotFound() {
        NotFoundException e = assertThrows(NotFoundException.class,
                () -> resumeService().getProfile(42L));
        assertTrue(e.getMessage().contains("42"));
    }

    @Test
    @DisplayName("PUT /api/resumes/{id}/profile 查无记录：同样收口 NotFoundException")
    void updateProfileMissingThrowsNotFound() {
        assertThrows(NotFoundException.class,
                () -> resumeService().updateProfile(42L, new ResumeProfile()));
    }

    @Test
    @DisplayName("GET /api/jds/{id} 查无记录：NotFoundException（修复前 IAE→400，语义错档）")
    void requireJdMissingThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> jdService().requireJd(7L));
    }

    @Test
    @DisplayName("GET /api/match/{id} 查无报告：NotFoundException（修复前 IAE→400）")
    void requireReportMissingThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> matchService().requireReport(99L));
    }

    @Test
    @DisplayName("POST /api/match 的 resumeId 查无记录：NotFoundException，不再是 400")
    void createMatchMissingResumeThrowsNotFound() {
        assertThrows(NotFoundException.class, () -> matchService().createMatch(1L, 1L));
    }

    @Test
    @DisplayName("POST /api/match 的 resume 存在但 jdId 查无：由 requireJd 收口 404")
    void createMatchMissingJdThrowsNotFound() {
        ResumeRecordMapper resumeMapper = mock(ResumeRecordMapper.class);
        com.resumecopilot.entity.ResumeRecord r = new com.resumecopilot.entity.ResumeRecord();
        r.setId(1L);
        r.setRawText("技能：Java");
        r.setProfileJson("{}");
        when(resumeMapper.selectById(1L)).thenReturn(r);
        MatchService svc = new MatchService(resumeMapper, mock(MatchReportMapper.class),
                jdService(), mock(SkillDictionaryService.class),
                new ScoreEngine(null, 0.85), new MatchProperties(), new ObjectMapper(),
                new LlmAvailability("sk-unit-test-real-key"));
        assertThrows(NotFoundException.class, () -> svc.createMatch(1L, 7L));
    }

    @Test
    @DisplayName(" NotFoundException 的 hint 可选：不带 hint 时不炸（advice 里 null hint 要过滤）")
    void hintIsOptional() {
        assertTrue(new NotFoundException("x").getHint() == null);
        List<String> none = List.of();
        assertTrue(none.isEmpty());
    }
}
