package com.resumecopilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.entity.JdRecord;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.entity.ResumeRecord;
import com.resumecopilot.jd.JdIngestService;
import com.resumecopilot.mapper.MatchReportMapper;
import com.resumecopilot.mapper.ResumeRecordMapper;
import com.resumecopilot.match.EvidenceValidator;
import com.resumecopilot.match.ScoreEngine;
import com.resumecopilot.match.SkillDictionary;
import com.resumecopilot.match.SkillDictionaryService;
import com.resumecopilot.model.JdRequirements;
import com.resumecopilot.model.MatchDetail;
import com.resumecopilot.model.ResumeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 匹配编排（计划书 4 章 record/match 交界）：取数 -> evidence 后置校验 -> 打分 -> 落 t_match_report。
 *
 * <p>注意校验发生在这里而不是抽取时：库里可能存着旧版 prompt（或未过后置校验的
 * 手工 PUT 校正）抽出的画像，匹配前重跑一遍 EvidenceValidator 让"证据必须来自原文"
 * 成为读路径上的不变量，而不只是写路径上的君子协定。被丢弃技能如实进报告。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchService {

    private final ResumeRecordMapper resumeMapper;
    private final MatchReportMapper reportMapper;
    private final JdIngestService jdIngestService;
    private final SkillDictionaryService dictionaryService;
    private final ScoreEngine scoreEngine;
    private final MatchProperties matchProperties;
    private final ObjectMapper objectMapper;
    private final LlmAvailability llmAvailability;

    @Value("${resume-copilot.prompt-version}")
    private String promptVersion;

    /** 报告留痕用：当前配置的对话模型名（纯规则通道时这只是声明，不代表本次调用了它） */
    @Value("${spring.ai.openai.chat.options.model:unknown}")
    private String chatModelName;

    /** 生成匹配报告并落库，返回报告记录 */
    public MatchReportRecord createMatch(Long resumeId, Long jdId) {
        ResumeRecord resume = resumeMapper.selectById(resumeId);
        if (resume == null) {
            // POST /api/match 的 resumeId 指向不存在资源：404 而非 400（id 格式没问题，是没这条记录）
            throw new NotFoundException("简历不存在: " + resumeId);
        }
        JdRecord jd = jdIngestService.requireJd(jdId);

        ResumeProfile profile = readProfile(resume);
        // 读路径不变量：证据不合格的技能先丢，再进打分
        List<String> discarded = EvidenceValidator.sanitize(profile, resume.getRawText());

        JdRequirements requirements = jdIngestService.parseStored(jd);
        SkillDictionary dict = dictionaryService.load();

        // 降级标注（GUI 测试遗留问题 3）：LLM 未配置（占位 key）时报告是纯规则通道产物，
        // meta.model 如实写 rule-only——把配置里声明的 gpt 模型名留在口径上，
        // 会让前端把"没调用过模型"的报告显示成"模型生成"，误导用户
        String effectiveModel = llmAvailability.chatConfigured() ? chatModelName : "rule-only";

        MatchDetail detail = scoreEngine.score(profile, discarded, requirements, dict,
                currentWeights(), promptVersion, effectiveModel);
        detail.setResumeId(resumeId);
        detail.setJdId(jdId);

        MatchReportRecord r = new MatchReportRecord();
        r.setResumeId(resumeId);
        r.setJdId(jdId);
        r.setTotalScore(detail.getTotalScore());
        r.setGrade(detail.getGrade());
        r.setDetailJson(writeJson(detail));
        r.setModel(effectiveModel);
        // 当前引擎不调对话模型：token 成本如实记 0（语义通道启用后回填 embedding 用量）
        r.setTokenCost(0);
        r.setPromptVersion(promptVersion);
        reportMapper.insert(r);
        log.info("match report {} created: resume {} x jd {} = {}({}), discarded={}",
                r.getId(), resumeId, jdId, detail.getTotalScore(), detail.getGrade(), discarded);
        return r;
    }

    /** 报告详情：原始记录 + 反序列化的明细 */
    public MatchReportRecord requireReport(Long id) {
        MatchReportRecord r = reportMapper.selectById(id);
        if (r == null) {
            // GET /api/match/{id} 与 SSE advice 共用本方法：查无报告统一 404（收口见 ApiExceptionAdvice）
            throw new NotFoundException("匹配报告不存在: " + id);
        }
        return r;
    }

    public MatchDetail readDetail(MatchReportRecord record) {
        try {
            return objectMapper.readValue(record.getDetailJson(), MatchDetail.class);
        } catch (Exception e) {
            throw new IllegalStateException("报告 detail_json 反序列化失败, reportId=" + record.getId(), e);
        }
    }

    /** 配置里的五维权重 -> 报告内嵌权重对象（和不为 1 是配置错误，大声失败） */
    private MatchDetail.Weights currentWeights() {
        double sum = matchProperties.getWeightSkill() + matchProperties.getWeightExperience()
                + matchProperties.getWeightYears() + matchProperties.getWeightDepth()
                + matchProperties.getWeightExpression();
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalStateException("五维权重之和必须为 1，当前为 " + sum
                    + "，请检查 resume-copilot.match.weight-* 配置");
        }
        MatchDetail.Weights w = new MatchDetail.Weights();
        w.setSkill(matchProperties.getWeightSkill());
        w.setExperience(matchProperties.getWeightExperience());
        w.setYears(matchProperties.getWeightYears());
        w.setDepth(matchProperties.getWeightDepth());
        w.setExpression(matchProperties.getWeightExpression());
        return w;
    }

    private ResumeProfile readProfile(ResumeRecord resume) {
        try {
            return objectMapper.readValue(resume.getProfileJson(), ResumeProfile.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历 profile_json 反序列化失败, resumeId=" + resume.getId(), e);
        }
    }

    private String writeJson(MatchDetail detail) {
        try {
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            throw new IllegalStateException("匹配明细序列化失败", e);
        }
    }
}
