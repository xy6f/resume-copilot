package com.resumecopilot.extract;

import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.model.ResumeProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * LLM 结构化抽取（计划书 5.1：Spring AI 结构化输出的落点）。
 *
 * <p>W1 先做"整篇一次抽取"，明确这是基线：计划书里准确率对比来自
 * W2 的"分块抽取 + 合并"迭代——基线先立起来才有得比。</p>
 *
 * <p>Prompt 三要素（都是可被追问的设计）：
 * 1. system 固定角色+纪律（只依据原文/无证据即丢弃）——幻觉防线一；
 * 2. evidence 字段强制回填原文——幻觉防线二，后置校验在 EvalRunner 里比对原文；
 * 3. temperature=0.1（配置里）+ entity() 走 JSON Schema——确定性防线。</p>
 */
@Slf4j
@Service
public class ResumeExtractionService {

    private static final String SYSTEM_PROMPT = """
            你是简历结构化引擎。只依据用户提供的简历原文抽取，禁止推测与补全。
            规则：
            1. 每个技能必须附 evidence=简历原文片段，找不到证据就不要输出该技能；
            2. level 归一化为：了解/熟悉/掌握/精通；
            3. experiences.highlights 只收录原文出现的量化数据；
            4. 原文没有的字段留空字符串/空列表，绝不编造。
            """;

    private final ChatClient chatClient;
    private final LlmAvailability availability;

    @Value("${resume-copilot.prompt-version}")
    private String promptVersion;

    // ChatClient.Builder 由 spring-ai starter 自动配置注入，build 后单例复用（线程安全）
    public ResumeExtractionService(ChatClient.Builder builder, LlmAvailability availability) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.availability = availability;
    }

    /**
     * 抽取：文本 -> ResumeProfile 对象。
     * .entity() 内部用 BeanOutputConverter：先按目标类生成 JSON Schema
     * 附加到请求约束模型输出，再把返回 JSON 反序列化——一次调用完成
     * "格式约束+解析"，替代手写脆弱的 JSON 提取。
     */
    public ResumeProfile extract(String resumeText) {
        // 门槛前置：与 JD 解析同一纪律——占位 key 不发网络请求，未配置直接 503+降级指引
        // （降级路径：POST /api/resumes/raw 走规则通道 lexical 抽取，质量有限但链路不断）
        availability.requireChatConfigured("简历抽取",
                "未配置 API Key 时可降级：改用 POST /api/resumes/raw 提交简历文本，"
                        + "走别名字典规则通道抽取（质量有限），或用 PUT /api/resumes/{id}/profile 手工提交结构化结果");
        log.debug("extract with prompt {}", promptVersion);
        return chatClient.prompt()
                .user(u -> u.text("请结构化这份简历原文：\n\n{resume}").param("resume", truncate(resumeText)))
                .call()
                .entity(ResumeProfile.class);
    }

    /** 上下文预算护栏：W1 粗暴截断（注释记债：W2 改分块），防一次塞爆 token */
    private String truncate(String text) {
        int max = 12000;
        return text.length() <= max ? text : text.substring(0, max) + "\n...(截断)";
    }
}
