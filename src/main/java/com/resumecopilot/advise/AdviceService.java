package com.resumecopilot.advise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.LlmCallFailedException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.entity.RewriteAdvice;
import com.resumecopilot.mapper.RewriteAdviceMapper;
import com.resumecopilot.model.MatchDetail;
import com.resumecopilot.service.MatchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.resumecopilot.common.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 改写建议生成（计划书 4 章 advise/ 与 5.3 改写 Prompt 约束）。
 *
 * <p>输入是已落库报告里的低分项（WEAK/MISSING，按配置 advice-max-items 截断，
 * token 成本护栏），输出走 SSE：每条建议一个流式段，段内逐 token 推
 * advice_chunk，段尾把整段文本连同"针对哪条要求/原句/依据"落 t_rewrite_advice——
 * 采纳率统计（ACCEPTED/IGNORED）从这张表长出来。</p>
 *
 * <p>降级纪律：LlmAvailability 门在构造 Flux 之前同步判定，未配置 key 时
 * 调用方拿到的是 503 JSON 而不是"开始流了再报错"——SSE 一旦开流，HTTP 状态码
 * 就冻结在 200，错误只能塞进事件体，用户界面看到的会是"莫名中断的流"。
 * 响亮地失败在还能响亮的时候。</p>
 */
@Slf4j
@Service
public class AdviceService {

    /** 改写 Prompt 的 system 约束（计划书 5.3 三条纪律的原文落点） */
    private static final String SYSTEM_PROMPT = """
            你是简历改写教练。规则：
            1. 只能依据给出的简历既有事实改写表述，严禁虚构未发生的项目、数据或经历；
            2. 输出的每条建议要说明"改动的考察点依据"（对应 JD 的哪条要求）；
            3. 用中文，口语化但克制，不喊口号；改写后的句子必须可直接粘贴回简历。
            """;

    private final ChatClient chatClient;
    private final LlmAvailability availability;
    private final MatchService matchService;
    private final RewriteAdviceMapper adviceMapper;
    private final MatchProperties matchProperties;
    private final ObjectMapper objectMapper;

    public AdviceService(ChatClient.Builder builder, LlmAvailability availability,
                         MatchService matchService, RewriteAdviceMapper adviceMapper,
                         MatchProperties matchProperties, ObjectMapper objectMapper) {
        // builder 注入即可用：真实调用被 availability 门保护，占位 key 不会走到网络上
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.availability = availability;
        this.matchService = matchService;
        this.adviceMapper = adviceMapper;
        this.matchProperties = matchProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 生成并流式推送改写建议。注意首两行是同步执行：报告不存在、门不过时
     * 异常在进入流式前抛出（由 ApiExceptionAdvice 转 404/503）。
     *
     * <p>判定顺序（GUI 回归修复）：先 requireReport 再 LLM 可用性门——
     * 反序时未配 key 的环境对任何 id（包括不存在的报告）一律返回 503，
     * "报告不存在"的 404 语义被 503 掩盖，客户端无法区分"资源写错"与"稍后配好 key 再来"。
     * 存在性检查与模型无关、成本为零，天然应当先行。</p>
     */
    public Flux<ServerSentEvent<String>> streamAdvice(Long reportId) {
        MatchReportRecord record = matchService.requireReport(reportId);
        availability.requireChatConfigured("改写建议",
                "匹配报告本身纯规则通道已可用；改写建议需配置 OPENAI_API_KEY 后重试");
        MatchDetail detail = matchService.readDetail(record);

        List<MatchDetail.RequirementOutcome> lowItems = pickLowScoreItems(detail);
        if (lowItems.isEmpty()) {
            // 没有低分项是好消息，也要明说——空流会让前端以为出错
            return Flux.just(sse("done", "{\"message\":\"该报告无低分项，无需改写建议\"}"));
        }

        List<Flux<ServerSentEvent<String>>> segments = new ArrayList<>();
        for (int i = 0; i < lowItems.size(); i++) {
            segments.add(oneItem(reportId, i, lowItems.get(i)));
        }
        segments.add(Flux.just(sse("done", "{\"reportId\":" + reportId + "}")));
        return Flux.concat(segments);
    }

    /**
     * 建议状态回写（W4 遗留：采纳率落表）：PATCH /api/advice/{id}/status 的服务层。
     *
     * <p>枚举口径以 schema.sql / 实体注释为准：PENDING/ACCEPTED/IGNORED——
     * ACCEPTED 即"采纳"（计划书与前端按钮文案用"采纳"，落库词形与既有埋点保持一致，
     * 不另造 ADOPTED 同义词，否则采纳率统计要认两套词形）。
     * 校验顺序与 streamAdvice 同款纪律：先入参枚举（400），再存在性（404）——
     * "非法状态"是客户端错误，与资源存不存在无关，先行判定不泄露存在性也不掩盖语义。</p>
     */
    public RewriteAdvice updateStatus(Long id, String status) {
        String normalized = status == null ? null : status.trim().toUpperCase();
        // 先判 null 再 contains：Set.of 是不可变集合，contains(null) 直接 NPE（单测抓到）
        if (normalized == null || !STATUS_WHITELIST.contains(normalized)) {
            throw new IllegalArgumentException(
                    "status 必须为 PENDING/ACCEPTED/IGNORED，收到: " + status);
        }
        RewriteAdvice advice = adviceMapper.selectById(id);
        if (advice == null) {
            // 查无此建议：统一 NotFoundException → 全局 advice 404（收口风格照旧）
            throw new NotFoundException("改写建议不存在: " + id);
        }
        advice.setStatus(normalized);
        adviceMapper.updateById(advice);
        log.info("advice {} status -> {}", id, normalized);
        return advice;
    }

    /** 状态白名单：与 schema.sql t_rewrite_advice.status 注释的三态一致 */
    private static final Set<String> STATUS_WHITELIST = Set.of("PENDING", "ACCEPTED", "IGNORED");

    /** 低分项：WEAK/MISSING 按原顺序取，上限 advice-max-items（成本护栏进配置不进代码） */
    private List<MatchDetail.RequirementOutcome> pickLowScoreItems(MatchDetail detail) {
        List<MatchDetail.RequirementOutcome> out = new ArrayList<>();
        for (MatchDetail.RequirementOutcome o : detail.getOutcomes()) {
            if (!ScoreStatuses.COVERED.equals(o.getStatus())) {
                out.add(o);
                if (out.size() >= matchProperties.getAdviceMaxItems()) {
                    break;
                }
            }
        }
        return out;
    }

    /** 单条低分项的流：逐 chunk 累积 -> 推 advice_chunk；流结束后整段落库并推 advice_item_done */
    private Flux<ServerSentEvent<String>> oneItem(Long reportId, int index,
                                                  MatchDetail.RequirementOutcome item) {
        StringBuilder accumulated = new StringBuilder();

        Flux<ServerSentEvent<String>> body = chatClient.prompt()
                .user(u -> u.text(buildUserPrompt(reportId, item)))
                .stream()
                .content()
                .map(chunk -> {
                    accumulated.append(chunk);
                    return sse("advice_chunk", chunk);
                });

        // 段尾：落库放在 concatWith 的延迟供给里——上一段真正推完才会执行
        Flux<ServerSentEvent<String>> tail = Flux.defer(() -> {
            RewriteAdvice advice = new RewriteAdvice();
            advice.setReportId(reportId);
            advice.setTargetBullet(item.getDescription().isBlank()
                    ? item.getRawSkill() : item.getDescription());
            advice.setOriginalText(String.join(" | ", item.getEvidences()));
            advice.setSuggestedText(accumulated.toString());
            advice.setReason("考察点：" + item.getSkill() + "（当前判定 " + item.getStatus()
                    + "，通道 " + item.getChannel() + "）");
            advice.setStatus("PENDING");
            adviceMapper.insert(advice);
            log.info("advice {} persisted for report {} item {}", advice.getId(), reportId, index);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("index", index);
            meta.put("adviceId", advice.getId());
            meta.put("targetSkill", item.getSkill());
            meta.put("status", item.getStatus());
            return Flux.just(sse("advice_item_done", writeJsonQuiet(meta)));
        });

        return body.onErrorResume(e -> {
            // 流中途失败：错误进 error 事件（此时状态码已 200，只能这么办），不再落半截库
            log.error("advice stream failed for report {}", reportId, e);
            return Flux.just(sse("error",
                    "{\"message\":\"模型流式调用失败：" + escape(e.getMessage()) + "\"}"));
        }).concatWith(tail);
    }

    /** 给模型的上下文：要求条目 + 简历侧已有证据，让"改写"锚定事实而非自由发挥 */
    private String buildUserPrompt(Long reportId, MatchDetail.RequirementOutcome item) {
        StringBuilder sb = new StringBuilder();
        sb.append("匹配报告 ").append(reportId).append(" 中一条低分项，请给出简历改写建议。\n");
        sb.append("JD 要求：").append(item.getRawSkill());
        if (!item.getDescription().isBlank()) {
            sb.append("（原文表述：").append(item.getDescription()).append("）");
        }
        sb.append("\n当前判定：").append(item.getStatus());
        if (item.getMinYears() > 0) {
            sb.append("，要求年限 ").append(item.getMinYears()).append(" 年");
        }
        sb.append("\n简历侧已有证据：");
        if (item.getEvidences().isEmpty()) {
            sb.append("（无——该项完全缺失，建议引导用户用真实做过的事补写，而不是无中生有）");
        } else {
            for (String ev : item.getEvidences()) {
                sb.append("\n- ").append(ev);
            }
        }
        sb.append("\n请直接给出可粘贴回简历的改写/补写表述与依据，不需要 JSON。");
        return sb.toString();
    }

    private static ServerSentEvent<String> sse(String event, String data) {
        return ServerSentEvent.<String>builder().event(event).data(data).build();
    }

    private String writeJsonQuiet(Map<String, Object> meta) {
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new LlmCallFailedException("建议元数据序列化失败", e);
        }
    }

    /** JSON 字符串里塞异常消息的最小转义（error 事件体不是模型输出，长度可控） */
    private static String escape(String s) {
        if (s == null) {
            return "unknown";
        }
        return s.replace("\\", "\\\\").replace("\"", "'").replace("\n", " ");
    }

    /** 状态常量别名：只在本类做"非 COVERED 即低分"的判定，避免误引 ScoreEngine 全量常量 */
    private static final class ScoreStatuses {
        static final String COVERED = "COVERED";
    }
}
