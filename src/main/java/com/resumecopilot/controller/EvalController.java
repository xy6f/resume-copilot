package com.resumecopilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.entity.EvalCase;
import com.resumecopilot.eval.EvalRunner;
import com.resumecopilot.eval.GoldenCase;
import com.resumecopilot.mapper.EvalCaseMapper;
import com.resumecopilot.match.SkillDictionaryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测跑批接口（计划书 7 章 #9 POST /api/eval/run，ADMIN 语义）。
 *
 * <p>单体 MVP 不养用户体系：用固定口令头 X-Admin-Token 表达"管理类接口"
 * （口令在配置 resume-copilot.admin-token，README 说明）。跑批本身全程确定性，
 * 不依赖模型——无 key 环境评测可用，这是把评测做成"基础设施"而非"附属报表"的取舍。</p>
 *
 * <p>副作用：跑批同时把用例 upsert 进 t_eval_case（计划书 6 章落库形态），
 * 指标 JSON 里的 promptVersion 与用例的 expected_json 一起构成版本化快照。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final SkillDictionaryService dictionaryService;
    private final EvalCaseMapper evalCaseMapper;
    private final ObjectMapper objectMapper;

    @Value("${resume-copilot.admin-token}")
    private String adminToken;

    @Value("${resume-copilot.prompt-version}")
    private String promptVersion;

    @Value("${resume-copilot.eval.golden-location}")
    private String goldenLocation;

    /** 请求体选填：{"promptVersion":"v1"} 可覆盖报告挂的版本号（版本对比场景） */
    public record RunRequest(String promptVersion) {
    }

    @PostMapping("/run")
    public Map<String, Object> run(@RequestHeader(value = "X-Admin-Token", required = false) String token,
                                   @RequestBody(required = false) RunRequest request) {
        if (token == null || !token.equals(adminToken)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "缺少或错误的 X-Admin-Token（管理口令见配置 resume-copilot.admin-token）");
        }
        EvalRunner runner = new EvalRunner(dictionaryService.load(), objectMapper);
        List<GoldenCase> cases = runner.loadGolden(goldenLocation);
        String version = request != null && request.promptVersion() != null
                && !request.promptVersion().isBlank() ? request.promptVersion() : promptVersion;

        Map<String, Object> report = new LinkedHashMap<>(runner.run(cases, version));
        report.put("goldenLocation", goldenLocation);
        int upserted = upsertCases(cases);
        report.put("evalCaseUpserted", upserted);
        log.info("eval run done: version={} cases={} recall={}", version, cases.size(),
                ((Map<?, ?>) report.get("aggregate")).get("skillRecall"));
        return report;
    }

    /** 用例落库（case_key 幂等 upsert）：表是"金标准的镜像"，文件才是事实源 */
    private int upsertCases(List<GoldenCase> cases) {
        int count = 0;
        for (GoldenCase c : cases) {
            try {
                Map<String, Object> expected = new LinkedHashMap<>();
                expected.put("expectedSkills", c.getExpectedSkills());
                expected.put("expectedBulletCount", c.getExpectedBulletCount());
                expected.put("note", c.getNote());
                EvalCase existing = evalCaseMapper.selectOne(
                        new LambdaQueryWrapper<EvalCase>().eq(EvalCase::getCaseKey, c.getId()));
                EvalCase row = existing == null ? new EvalCase() : existing;
                row.setCaseKey(c.getId());
                row.setResumeText(c.getResumeText());
                row.setExpectedJson(objectMapper.writeValueAsString(expected));
                row.setNote(c.getNote() == null ? "" : c.getNote());
                if (existing == null) {
                    evalCaseMapper.insert(row);
                } else {
                    evalCaseMapper.updateById(row);
                }
                count++;
            } catch (Exception e) {
                // 单条落库失败不毁整批评测：指标已经算完，落库只是镜像
                log.warn("eval case {} upsert failed: {}", c.getId(), e.getMessage());
            }
        }
        return count;
    }
}
