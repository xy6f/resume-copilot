package com.resumecopilot.service;

import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.entity.ResumeRecord;
import com.resumecopilot.mapper.MatchReportMapper;
import com.resumecopilot.mapper.ResumeRecordMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 历史版本对比（W4 遗留）单测：分组口径=rawText SHA-256 指纹；
 * 钉四件事——同指纹聚合/异文本剔除、倒序截最近 5 条且返回正序、
 * 每条挂最新一条报告的 totalScore（多条取 id 最大者）、查无此简历 404 收口。
 */
class ResumeHistoryServiceTest {

    private final ResumeRecordMapper resumeMapper = mock(ResumeRecordMapper.class);
    private final MatchReportMapper reportMapper = mock(MatchReportMapper.class);
    private final ResumeHistoryService svc = new ResumeHistoryService(resumeMapper, reportMapper);

    private ResumeRecord resume(long id, String text, LocalDateTime at) {
        ResumeRecord r = new ResumeRecord();
        r.setId(id);
        r.setFileName("demo.txt");
        r.setRawText(text);
        r.setPromptVersion("v1-LEX");
        r.setCreatedAt(at);
        return r;
    }

    private MatchReportRecord report(long id, long resumeId, int score, LocalDateTime at) {
        MatchReportRecord m = new MatchReportRecord();
        m.setId(id);
        m.setResumeId(resumeId);
        m.setTotalScore(score);
        m.setGrade("良好");
        m.setCreatedAt(at);
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> out) {
        return (List<Map<String, Object>>) out.get("items");
    }

    @Test
    @DisplayName("同指纹聚合：三种文本只归组同一段的两条，异文本剔除")
    void groupsByFingerprint() {
        ResumeRecord a1 = resume(1L, "技能：Java", LocalDateTime.of(2026, 10, 1, 9, 0));
        ResumeRecord a2 = resume(2L, "技能：Java", LocalDateTime.of(2026, 10, 2, 9, 0));
        ResumeRecord other = resume(3L, "技能：Python", LocalDateTime.of(2026, 10, 3, 9, 0));
        when(resumeMapper.selectById(2L)).thenReturn(a2);
        when(resumeMapper.selectList(any())).thenReturn(List.of(a1, other, a2));
        when(reportMapper.selectList(any())).thenReturn(List.of(
                report(11L, 1L, 60, null), report(12L, 2L, 75, null)));

        Map<String, Object> out = svc.history(2L);
        List<Map<String, Object>> list = items(out);
        assertEquals(2, list.size());
        assertEquals(1L, list.get(0).get("resumeId"));
        assertEquals(2L, list.get(1).get("resumeId"));   // 时间正序，前端直接画线
        assertEquals(60, list.get(0).get("totalScore"));
        assertEquals(75, list.get(1).get("totalScore"));
        assertEquals("良好", list.get(1).get("grade"));
    }

    @Test
    @DisplayName("超过 5 次摄取：倒序截最近 5 条，返回仍按时间正序")
    void capsAtLatestFive() {
        List<ResumeRecord> all = new ArrayList<>();
        for (int i = 1; i <= 7; i++) {
            all.add(resume(i, "同一份简历", LocalDateTime.of(2026, 10, i % 28 + 1, 8, 0)));
        }
        when(resumeMapper.selectById(7L)).thenReturn(all.get(6));
        when(resumeMapper.selectList(any())).thenReturn(all);
        when(reportMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> list = items(svc.history(7L));
        assertEquals(5, list.size());
        assertEquals(3L, list.get(0).get("resumeId"));   // 丢弃最旧的两条(id 1、2)
        assertEquals(7L, list.get(4).get("resumeId"));
    }

    @Test
    @DisplayName("一条简历多份报告：挂 id 最大（最新）那条的 totalScore")
    void linksLatestReport() {
        ResumeRecord r = resume(5L, "text-x", LocalDateTime.of(2026, 10, 5, 8, 0));
        when(resumeMapper.selectById(5L)).thenReturn(r);
        when(resumeMapper.selectList(any())).thenReturn(List.of(r));
        when(reportMapper.selectList(any())).thenReturn(List.of(
                report(20L, 5L, 40, null), report(21L, 5L, 88, null), report(19L, 5L, 30, null)));

        Map<String, Object> item = items(svc.history(5L)).get(0);
        assertEquals(21L, item.get("reportId"));
        assertEquals(88, item.get("totalScore"));
    }

    @Test
    @DisplayName("从未出过报告：totalScore/reportId/grade 如实为 null（曲线标注未评分）")
    void missingReportYieldsNullScore() {
        ResumeRecord r = resume(6L, "text-y", LocalDateTime.of(2026, 10, 6, 8, 0));
        when(resumeMapper.selectById(6L)).thenReturn(r);
        when(resumeMapper.selectList(any())).thenReturn(List.of(r));
        when(reportMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> item = items(svc.history(6L)).get(0);
        assertNull(item.get("reportId"));
        assertNull(item.get("totalScore"));
        assertNull(item.get("grade"));
    }

    @Test
    @DisplayName("查无此简历：NotFoundException（404 收口风格照旧）")
    void missingResumeThrowsNotFound() {
        when(resumeMapper.selectById(any())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> svc.history(404L));
    }

    @Test
    @DisplayName("指纹稳定性：同文本恒同 sha，异文本不同（分组口径的公理）")
    void fingerprintStable() {
        String h1 = ResumeHistoryService.sha256Hex("技能：Java");
        String h2 = ResumeHistoryService.sha256Hex("技能：Java");
        String h3 = ResumeHistoryService.sha256Hex("技能： java");
        assertEquals(h1, h2);
        assertTrue(!h1.equals(h3));
        assertEquals(64, h1.length());
    }
}
