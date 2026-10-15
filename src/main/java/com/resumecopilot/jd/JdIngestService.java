package com.resumecopilot.jd;

import com.resumecopilot.common.JdCreateRequest;
import com.resumecopilot.common.NotFoundException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.entity.JdRecord;
import com.resumecopilot.mapper.JdRecordMapper;
import com.resumecopilot.model.JdRequirements;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JD 录入编排（计划书 4 章 jd/ 链）：文本 -> （LLM 解析 | 直接采用提交条目）-> t_jd。
 *
 * <p>返回体里的 parseBy 字段把"这次是谁干的活"显式告诉调用方：
 * LLM=模型解析，SUBMITTED=用户提交的结构化条目（降级或校正）。
 * 可追溯性从简历链路（prompt_version）延续到 JD 链路（落库来源），一致的设计语言。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JdIngestService {

    private final JdParsingService parsingService;
    private final JdRecordMapper jdMapper;
    private final ObjectMapper objectMapper;

    /** 录入 JD，返回 {jdId, parseBy, requirements} */
    public Map<String, Object> create(JdCreateRequest req) {
        JdRequirements requirements;
        String parseBy;
        if (req.getRequirements() != null && !req.getRequirements().isEmpty()) {
            // 降级/校正路径：提交即用，完全不碰 LLM——无 key 环境 JD 功能照样可用
            requirements = new JdRequirements();
            requirements.setRequirements(req.getRequirements());
            parseBy = "SUBMITTED";
        } else {
            requirements = parsingService.parse(req.getRawText());
            if (requirements == null || requirements.getRequirements() == null) {
                requirements = new JdRequirements();
            }
            parseBy = "LLM";
        }
        // 空要求条目也允许入库（JD 可能就是没写硬要求），但响应里如实反映，不假装解析成功
        JdRecord r = new JdRecord();
        r.setTitle(req.getTitle() == null ? "" : req.getTitle());
        r.setCompany(req.getCompany() == null ? "" : req.getCompany());
        r.setRawText(req.getRawText());
        r.setReqJson(writeJson(requirements));
        r.setSource(req.getSource() == null ? "TEXT" : req.getSource());
        jdMapper.insert(r);
        log.info("jd {} created, parseBy={}, {} requirements", r.getId(), parseBy,
                requirements.getRequirements().size());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("jdId", r.getId());
        out.put("parseBy", parseBy);
        out.put("requirements", requirements.getRequirements());
        return out;
    }

    /** 读取 JD 记录（匹配与报告接口都要用） */
    public JdRecord requireJd(Long id) {
        JdRecord r = jdMapper.selectById(id);
        if (r == null) {
            // 同类缺陷收口：原抛 IllegalArgumentException 被全局 advice 当"入参错误"返 400，
            // 但"GET /api/jds/{id} 查无此资源"应是 404——改抛 NotFoundException 由 advice 统一映射
            throw new NotFoundException("JD 不存在: " + id);
        }
        return r;
    }

    /** req_json 反序列化为要求条目模型 */
    public JdRequirements parseStored(JdRecord record) {
        try {
            return objectMapper.readValue(record.getReqJson(), JdRequirements.class);
        } catch (Exception e) {
            throw new IllegalStateException("JD req_json 反序列化失败, jdId=" + record.getId(), e);
        }
    }

    private String writeJson(JdRequirements requirements) {
        try {
            return objectMapper.writeValueAsString(requirements);
        } catch (Exception e) {
            throw new IllegalStateException("JD 要求条目序列化失败", e);
        }
    }
}
