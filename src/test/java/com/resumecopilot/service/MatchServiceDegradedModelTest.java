package com.resumecopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.entity.JdRecord;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.entity.ResumeRecord;
import com.resumecopilot.jd.JdIngestService;
import com.resumecopilot.mapper.MatchReportMapper;
import com.resumecopilot.mapper.ResumeRecordMapper;
import com.resumecopilot.match.ScoreEngine;
import com.resumecopilot.match.SeedDictionaryTestSupport;
import com.resumecopilot.match.SkillDictionaryService;
import com.resumecopilot.model.JdRequirements;
import com.resumecopilot.model.MatchDetail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 降级标注口径单测（GUI 回归遗留问题 3）：LLM 未配置（占位 key）时，
 * 匹配报告 meta.model 必须如实写 rule-only，而不是配置里声明的 gpt 模型名——
 * 报告是纯规则通道产物，前端显示"模型生成"会误导用户。
 */
class MatchServiceDegradedModelTest {

    /** 组装一个只缺"LLM 是否配置"这一变量的 MatchService */
    private MatchReportRecord createWith(boolean chatConfigured) throws Exception {
        ResumeRecordMapper resumeMapper = mock(ResumeRecordMapper.class);
        ResumeRecord resume = new ResumeRecord();
        resume.setId(1L);
        resume.setRawText("技能：Java");
        resume.setProfileJson("{}");
        when(resumeMapper.selectById(1L)).thenReturn(resume);

        JdIngestService jdIngestService = mock(JdIngestService.class);
        JdRecord jd = new JdRecord();
        jd.setId(2L);
        jd.setRawText("要求：熟悉 Java");
        when(jdIngestService.requireJd(2L)).thenReturn(jd);
        when(jdIngestService.parseStored(any())).thenReturn(new JdRequirements());

        SkillDictionaryService dictionaryService = mock(SkillDictionaryService.class);
        when(dictionaryService.load()).thenReturn(SeedDictionaryTestSupport.fromSchemaSql());

        MatchService svc = new MatchService(resumeMapper, mock(MatchReportMapper.class),
                jdIngestService, dictionaryService,
                new ScoreEngine(null, 0.85), new MatchProperties(), new ObjectMapper(),
                new LlmAvailability(chatConfigured
                        ? "sk-unit-test-real-key" : LlmAvailability.PLACEHOLDER_KEY));
        // @Value 字段不走构造器：手工注入与 application.yml 同形的配置值
        ReflectionTestUtils.setField(svc, "promptVersion", "v1-test");
        ReflectionTestUtils.setField(svc, "chatModelName", "gpt-4o-mini");
        return svc.createMatch(1L, 2L);
    }

    @Test
    @DisplayName("未配 key（degraded）：记录与明细的 model 都是 rule-only，不是 gpt-4o-mini")
    void degradedReportMarksRuleOnly() throws Exception {
        MatchReportRecord record = createWith(false);
        assertEquals("rule-only", record.getModel());

        ObjectMapper om = new ObjectMapper();
        MatchDetail detail = om.readValue(record.getDetailJson(), MatchDetail.class);
        assertEquals("rule-only", detail.getModel());
    }

    @Test
    @DisplayName("配了 key：保持声明的模型名（rule-only 只在降级时生效）")
    void configuredReportKeepsModelName() throws Exception {
        MatchReportRecord record = createWith(true);
        assertEquals("gpt-4o-mini", record.getModel());
    }
}
