package com.resumecopilot.match;

import com.resumecopilot.model.JdRequirements;
import com.resumecopilot.model.MatchDetail;
import com.resumecopilot.model.ResumeProfile;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 匹配打分引擎（计划书 4 章 match/ScoreEngine）：纯 Java、不碰 DB、不碰 Spring 上下文，
 * 输入 (已校验的 ResumeProfile, JdRequirements, 别名字典) 输出完整 {@link MatchDetail}，
 * 因而是单测的直接对象（ScoreEngineTest 手算对分）。
 *
 * <p>双通道（计划书 5.2）：
 * 1. 规则通道（本类内建，永远启用）：别名归一后的精确/包含匹配 + 经历文本兜底；
 * 2. 语义通道（{@link EmbeddingModel} 可空注入）：Spring AI 的 embedding bean 在
 *    spring.ai.model.embedding=none（未配 key 的默认态）时根本不存在，构造时拿到 null，
 *    引擎跳过语义并在报告 semanticNote 里写明"语义通道未启用"——降级写进产物，不静默。</p>
 *
 * <p>每条 JD 要求的三态判定顺序（可信度从高到低，先到先得）：
 * COVERED(规则-精确) > WEAK(规则-包含) > WEAK(规则-经历文本) > WEAK(语义-阈值) > MISSING。
 * 语义通道只给"弱覆盖"、不给"覆盖"：Embedding 相似不等于简历里真写过，
 * 让它独自撑起满分是对不确定性的纵容（面试可讲这层克制）。</p>
 */
public class ScoreEngine {

    public static final String STATUS_COVERED = "COVERED";
    public static final String STATUS_WEAK = "WEAK";
    public static final String STATUS_MISSING = "MISSING";

    public static final String CHANNEL_RULE = "RULE";
    public static final String CHANNEL_SEMANTIC = "SEMANTIC";
    public static final String CHANNEL_NONE = "NONE";

    private final EmbeddingModel embeddingModel;      // 可空：null=语义通道未启用
    private final double semanticThreshold;

    public ScoreEngine(EmbeddingModel embeddingModel, double semanticThreshold) {
        this.embeddingModel = embeddingModel;
        this.semanticThreshold = semanticThreshold;
    }

    /**
     * 打分主入口。
     *
     * @param profile        已通过 {@link EvidenceValidator#sanitize} 清洗的简历画像
     * @param discarded      清洗时丢弃的技能名（幻觉嫌疑），原样记进报告留痕
     * @param jd             JD 结构化要求
     * @param dict           规则通道字典
     * @param weights        五维权重（来自配置，报告里整组留痕）
     * @param promptVersion  报告出身：与简历版本、字典规模构成可复现三元组
     * @param modelDeclared  当次配置的对话模型名（纯规则通道时的声明式记录）
     */
    public MatchDetail score(ResumeProfile profile, List<String> discarded,
                             JdRequirements jd, SkillDictionary dict,
                             MatchDetail.Weights weights,
                             String promptVersion, String modelDeclared) {
        MatchDetail detail = new MatchDetail();
        detail.setPromptVersion(promptVersion);
        detail.setModel(modelDeclared);
        detail.setAliasDictionarySize(dict.size());
        detail.setDiscardedSkills(discarded == null ? new ArrayList<>() : new ArrayList<>(discarded));
        detail.setWeights(weights);

        List<JdRequirements.Requirement> reqs =
                jd == null || jd.getRequirements() == null ? new ArrayList<>() : jd.getRequirements();

        // ---- 语义通道状态先行落报告 ----
        if (embeddingModel == null) {
            detail.setSemanticChannelEnabled(false);
            detail.setSemanticNote("语义通道未启用（EmbeddingModel 未配置，仅规则通道出分）");
        } else {
            detail.setSemanticChannelEnabled(true);
            detail.setSemanticNote("语义通道已启用，判弱阈值 cosine>=" + semanticThreshold);
        }

        // ---- 覆盖矩阵：逐条要求判三态 ----
        Map<String, float[]> skillVecCache = new LinkedHashMap<>();
        int covered = 0;
        int weak = 0;
        for (JdRequirements.Requirement r : reqs) {
            MatchDetail.RequirementOutcome o = judgeOne(profile, r, dict, skillVecCache);
            detail.getOutcomes().add(o);
            if (STATUS_COVERED.equals(o.getStatus())) {
                covered++;
            } else if (STATUS_WEAK.equals(o.getStatus())) {
                weak++;
            }
        }

        // ---- 五维打分（公式全部展开在注释里，测试用同一公式手算）----
        MatchDetail.Dimensions d = detail.getDimensions();

        // 1) 技能覆盖：COVERED 记满分、WEAK 记半分，除以要求数。
        //    covered*1.0 + weak*0.5 再 *100/N。N=0 时给 50 中性分：
        //    "JD 没写硬技能要求"不该奖励也不该惩罚一份简历。
        d.setSkillCoverage(reqs.isEmpty() ? 50
                : (int) Math.round(100.0 * (covered + 0.5 * weak) / reqs.size()));

        // 2) 经历相关性：要求标准名（或其别名）出现在任一段经历的文本里记 1 次命中。
        //    满分线=半数要求命中（至少 2 条），再多不加分——实习简历经历有限，
        //    "命中一半要求已经是很强的经历信号"是这套权重的立场。
        d.setExperienceRelevance(reqs.isEmpty() ? 50
                : experienceRelevance(profile, reqs, dict));

        // 3) 年限匹配：无任何 minYears 要求记满分 100；否则用粗代理
        //    estimatedYears = 经历段数 * 1 年（每段实习/项目按 1 年当量，MVP 口径，
        //    period 时间解析在迭代债清单）。得分 = 100 * min(1, estimated/maxMinYears)。
        d.setYearsFit(yearsFit(profile, reqs));

        // 4) 项目深度：每段经历 item = min(100, 20*bullet数 + 25*量化点数)（有结构、
        //    有数字才有深度），取均值；无经历给 20 地板分而不是 0——"没写经历"是表达问题，
        //    "没有经历"不归本报告管。
        d.setProjectDepth(projectDepth(profile));

        // 5) 表达质量：全部 bullet 中"长度 15~120 字且含数字"的记 good，
        //    得分 = 20 + 80*good/total（地板 20：有表达就有基础分）；无 bullet 给 20。
        d.setExpressionQuality(expressionQuality(profile));

        // 总分 = 五维加权和四舍五入；评级线（85/70/55）与 README 一致
        double total = weights.getSkill() * d.getSkillCoverage()
                + weights.getExperience() * d.getExperienceRelevance()
                + weights.getYears() * d.getYearsFit()
                + weights.getDepth() * d.getProjectDepth()
                + weights.getExpression() * d.getExpressionQuality();
        detail.setTotalScore((int) Math.round(total));
        detail.setGrade(gradeOf(detail.getTotalScore()));
        return detail;
    }

    /** 单条 JD 要求的三态判定（顺序即可信度顺序，见类注释） */
    private MatchDetail.RequirementOutcome judgeOne(ResumeProfile profile,
                                                    JdRequirements.Requirement r,
                                                    SkillDictionary dict,
                                                    Map<String, float[]> skillVecCache) {
        MatchDetail.RequirementOutcome o = new MatchDetail.RequirementOutcome();
        String rawSkill = r.getSkill() == null ? "" : r.getSkill().trim();
        o.setRawSkill(rawSkill);
        o.setDescription(r.getDescription() == null ? "" : r.getDescription());
        o.setMinYears(r.getMinYears());
        String reqCanon = dict.canonical(rawSkill);
        o.setSkill(reqCanon);
        o.setStatus(STATUS_MISSING);
        o.setChannel(CHANNEL_NONE);

        // 通道 A：技能列表精确命中（两侧归一到同一标准名）
        List<String> exactEvidence = new ArrayList<>();
        for (ResumeProfile.Skill s : profile.getSkills()) {
            if (dict.sameStandard(s.getName(), rawSkill)) {
                exactEvidence.add(s.getName() + "（" + s.getEvidence() + "）");
            }
        }
        if (!exactEvidence.isEmpty()) {
            o.setStatus(STATUS_COVERED);
            o.setChannel(CHANNEL_RULE);
            o.setEvidences(exactEvidence);
            return o;
        }

        // 通道 B：技能列表包含式弱命中（Java 之于 Java并发：粒度不足但方向对）
        for (ResumeProfile.Skill s : profile.getSkills()) {
            if (dict.subsuming(dict.canonical(s.getName()), reqCanon)) {
                o.setStatus(STATUS_WEAK);
                o.setChannel(CHANNEL_RULE);
                o.getEvidences().add(s.getName() + "（" + s.getEvidence() + "）");
                return o;
            }
        }

        // 通道 C：经历文本兜底——技能列表没写但 bullet 里真干过（别名族任一出现在文本即算）
        String hitBullet = findExperienceHit(profile, reqCanon, dict);
        if (hitBullet != null) {
            o.setStatus(STATUS_WEAK);
            o.setChannel(CHANNEL_RULE);
            o.getEvidences().add(hitBullet);
            return o;
        }

        // 通道 D：语义兜底——只把 MISSING 升成 WEAK，永远不给 COVERED（见类注释的克制）
        if (embeddingModel != null && !profile.getSkills().isEmpty()) {
            Double best = bestSimilarity(r, profile, skillVecCache);
            if (best != null && best >= semanticThreshold) {
                o.setStatus(STATUS_WEAK);
                o.setChannel(CHANNEL_SEMANTIC);
                o.setSimilarity(round2(best));
            }
        }
        return o;
    }

    /** 经历文本兜底：把每段经历的 title/bullets/highlights 拼成归一化文本，
     *  要求标准名自身或它的任一"够长的别名"出现在文本中即命中，返回首个命中的原文 bullet */
    private String findExperienceHit(ResumeProfile profile, String reqCanon, SkillDictionary dict) {
        List<String> needles = new ArrayList<>();
        needles.add(SkillDictionary.normalize(reqCanon));
        for (String alias : dict.aliasesOfStandard(reqCanon)) {
            if (alias.length() >= SkillDictionary.MIN_CONTAIN_LEN) {
                needles.add(alias);
            }
        }
        for (ResumeProfile.Experience e : profile.getExperiences()) {
            List<String> texts = new ArrayList<>();
            texts.add(e.getTitle());
            texts.addAll(e.getBullets());
            texts.addAll(e.getHighlights());
            for (String t : texts) {
                if (t == null || t.isBlank()) {
                    continue;
                }
                String norm = SkillDictionary.normalize(t);
                for (String needle : needles) {
                    if (!needle.isEmpty() && norm.contains(needle)) {
                        return "经历命中：" + t;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 语义相似度：要求文本（skill + description）向量与每个简历技能名向量的余弦最大值。
     * 单条 embedding 调用失败不让整份报告炸掉：返回 null 视作"这条没算"，
     * 上层继续走 MISSING——语义是增益通道，不是承重墙。
     */
    private Double bestSimilarity(JdRequirements.Requirement r, ResumeProfile profile,
                                  Map<String, float[]> cache) {
        try {
            float[] reqVec = embeddingModel.embed(
                    (r.getSkill() == null ? "" : r.getSkill()) + " "
                            + (r.getDescription() == null ? "" : r.getDescription()));
            double best = -1;
            for (ResumeProfile.Skill s : profile.getSkills()) {
                float[] sv = cache.computeIfAbsent(s.getName(), embeddingModel::embed);
                double sim = cosine(reqVec, sv);
                if (sim > best) {
                    best = sim;
                }
            }
            return best < 0 ? null : best;
        } catch (Exception e) {
            return null;
        }
    }

    /** 余弦相似度：dot/(|a|*|b|)，任一向量为零向量返回 0（无方向可言） */
    static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0.0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    /** 维度 2：满分线 = max(2, ceil(N/2)) 次命中；命中=要求标准名或其别名出现在经历文本 */
    private int experienceRelevance(ResumeProfile profile, List<JdRequirements.Requirement> reqs,
                                    SkillDictionary dict) {
        StringBuilder sb = new StringBuilder();
        for (ResumeProfile.Experience e : profile.getExperiences()) {
            if (e.getTitle() != null) {
                sb.append(e.getTitle()).append('\n');
            }
            e.getBullets().forEach(b -> sb.append(b).append('\n'));
            e.getHighlights().forEach(h -> sb.append(h).append('\n'));
        }
        String all = SkillDictionary.normalize(sb.toString());
        int hits = 0;
        for (JdRequirements.Requirement r : reqs) {
            String canon = SkillDictionary.normalize(dict.canonical(r.getSkill()));
            boolean hit = !canon.isEmpty() && all.contains(canon);
            if (!hit) {
                for (String alias : dict.aliasesOfStandard(dict.canonical(r.getSkill()))) {
                    if (alias.length() >= SkillDictionary.MIN_CONTAIN_LEN && all.contains(alias)) {
                        hit = true;
                        break;
                    }
                }
            }
            if (hit) {
                hits++;
            }
        }
        double fullMark = Math.max(2, Math.ceil(reqs.size() / 2.0));
        return (int) Math.round(100.0 * Math.min(1.0, hits / fullMark));
    }

    /** 维度 3：年限匹配。maxMinYears=0 记满分；否则 100*min(1, 经历段数/maxMinYears) */
    private int yearsFit(ResumeProfile profile, List<JdRequirements.Requirement> reqs) {
        int maxMinYears = 0;
        for (JdRequirements.Requirement r : reqs) {
            maxMinYears = Math.max(maxMinYears, r.getMinYears());
        }
        if (maxMinYears <= 0) {
            return 100;
        }
        double estimatedYears = profile.getExperiences().size() * 1.0;
        return (int) Math.round(100.0 * Math.min(1.0, estimatedYears / maxMinYears));
    }

    /** 维度 4：项目深度。每段 min(100, 20*bullets + 25*highlights)，取均值；无经历 20 */
    private int projectDepth(ResumeProfile profile) {
        List<ResumeProfile.Experience> exps = profile.getExperiences();
        if (exps.isEmpty()) {
            return 20;
        }
        double sum = 0;
        for (ResumeProfile.Experience e : exps) {
            double item = Math.min(100.0, 20.0 * e.getBullets().size() + 25.0 * e.getHighlights().size());
            sum += item;
        }
        return (int) Math.round(sum / exps.size());
    }

    /** 维度 5：表达质量。20 + 80 * (good/total)；good=长度15~120且含数字；无bullet给20地板 */
    private int expressionQuality(ResumeProfile profile) {
        List<String> bullets = new ArrayList<>();
        for (ResumeProfile.Experience e : profile.getExperiences()) {
            bullets.addAll(e.getBullets());
        }
        if (bullets.isEmpty()) {
            return 20;
        }
        int good = 0;
        for (String b : bullets) {
            int len = b == null ? 0 : b.trim().length();
            boolean hasDigit = b != null && b.chars().anyMatch(Character::isDigit);
            if (len >= 15 && len <= 120 && hasDigit) {
                good++;
            }
        }
        return (int) Math.round(20 + 80.0 * good / bullets.size());
    }

    /** 评级线：>=85 优秀 / >=70 良好 / >=55 一般 / 其余 较差（README 同步口径） */
    public static String gradeOf(int totalScore) {
        if (totalScore >= 85) {
            return "优秀";
        }
        if (totalScore >= 70) {
            return "良好";
        }
        if (totalScore >= 55) {
            return "一般";
        }
        return "较差";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
