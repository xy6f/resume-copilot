package com.resumecopilot.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.entity.ResumeRecord;
import com.resumecopilot.mapper.MatchReportMapper;
import com.resumecopilot.mapper.ResumeRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 历史版本对比（W4 遗留：给前端画分数曲线）：GET /api/resumes/{id}/history。
 *
 * <p>版本链选型（读表后的取舍，写在这里防止后人再问）：
 * t_resume 没有 sourceId 列，项目也没有用户维度（无鉴权体系），fileName 由前端
 * 写死（frontend-demo.txt）不足以界定"同一用户"；PUT /{id}/profile 校正是
 * 原地替换 profile_json，不新增行——校正根本不构成"版本"，不进此链。
 * 因此最诚实、零改表的分组口径就是 rawText 的 SHA-256 指纹：
 * 同一份简历文本的重复摄取（前端"一键填充示例→摄取"多次点击）天然同指纹。</p>
 *
 * <p>规模说明：候选集是全表扫一遍在 Java 侧算指纹——rawText 是 MEDIUMTEXT，
 * SQL 侧（MySQL SHA2）与 Java 侧对"UTF-8 字节、大小写 hex"的口径极易漂移，
 * 宁可全量算也不让两套指纹口径各自为政；当前是单机实习项目数据量，够用。
 * 取"创建时间倒序前 5 条"，返回时按时间正序排好，前端直接画折线。</p>
 */
@Service
@RequiredArgsConstructor
public class ResumeHistoryService {

    /** 曲线最多回看的摄取次数（计划书 W4：分数趋势看最近几版即可） */
    static final int MAX_ITEMS = 5;

    private final ResumeRecordMapper resumeMapper;
    private final MatchReportMapper reportMapper;

    /**
     * 返回 {resumeId, fingerprint, items:[{resumeId, fileName, promptVersion,
     * createdAt, reportId, totalScore, grade}]}；同指纹但从未出过报告的条目，
     * reportId/totalScore/grade 如实为 null（画曲线时该点标注"未评分"）。
     */
    public Map<String, Object> history(Long id) {
        ResumeRecord target = resumeMapper.selectById(id);
        if (target == null) {
            // 收口风格照旧：查无此简历 NotFoundException → 全局 advice 404
            throw new NotFoundException("简历记录不存在: " + id);
        }
        String fingerprint = sha256Hex(target.getRawText());

        // 1. 同指纹的摄取记录：倒序取最近 MAX_ITEMS 条，再转正序供画线
        List<ResumeRecord> sameFingerprint = new ArrayList<>();
        for (ResumeRecord r : resumeMapper.selectList(null)) {
            if (fingerprint.equals(sha256Hex(r.getRawText()))) {
                sameFingerprint.add(r);
            }
        }
        sameFingerprint.sort(Comparator
                .comparing(ResumeRecord::getCreatedAt,
                        Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
                .thenComparing(ResumeRecord::getId));
        if (sameFingerprint.size() > MAX_ITEMS) {
            sameFingerprint = sameFingerprint.subList(sameFingerprint.size() - MAX_ITEMS,
                    sameFingerprint.size());
        }

        // 2. 一次 in 查询取回全部候选报告，Java 侧按 resumeId 归组留最新（id 自增即时间序）
        Map<Long, MatchReportRecord> latestByResume = new HashMap<>();
        if (!sameFingerprint.isEmpty()) {
            List<Long> ids = sameFingerprint.stream().map(ResumeRecord::getId).toList();
            for (MatchReportRecord report : reportMapper.selectList(
                    new QueryWrapper<MatchReportRecord>().in("resume_id", ids))) {
                MatchReportRecord kept = latestByResume.get(report.getResumeId());
                if (kept == null || report.getId() > kept.getId()) {
                    latestByResume.put(report.getResumeId(), report);
                }
            }
        }

        List<Map<String, Object>> items = new ArrayList<>();
        for (ResumeRecord r : sameFingerprint) {
            MatchReportRecord report = latestByResume.get(r.getId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("resumeId", r.getId());
            item.put("fileName", r.getFileName());
            item.put("promptVersion", r.getPromptVersion());
            item.put("createdAt", r.getCreatedAt());
            item.put("reportId", report == null ? null : report.getId());
            item.put("totalScore", report == null ? null : report.getTotalScore());
            item.put("grade", report == null ? null : report.getGrade());
            items.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("resumeId", id);
        out.put("fingerprint", fingerprint);
        out.put("items", items);
        return out;
    }

    /** rawText 指纹：UTF-8 字节 SHA-256 小写 hex——同一段文本必然同指纹 */
    static String sha256Hex(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必备算法，理论不可达
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
