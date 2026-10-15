package com.resumecopilot.jd;

import com.resumecopilot.common.LlmCallFailedException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.model.JdRequirements;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * JD -> 要求条目数组 的 LLM 解析（与简历抽取同范式：结构化输出 + 低温度 + 只依据原文）。
 *
 * <p>调用前过 {@link LlmAvailability} 门：OPENAI_API_KEY 未配置直接 503，
 * 上层（{@link JdIngestService}）会把"允许直接提交已结构化 requirements"
 * 这条降级路径写进错误 hint——错误响应即使用说明书。</p>
 */
@Slf4j
@Service
public class JdParsingService {

    private static final String SYSTEM_PROMPT = """
            你是 JD（职位描述）结构化引擎。只依据用户提供的 JD 原文抽取，禁止推测与补全。
            规则：
            1. requirements 每条含 skill（规范技能名，如 Java并发/Redis/MySQL，尽量短）、
               description（JD 原文表述摘录）、minYears（该条隐含的经验年限，没写填 0）；
            2. 只收"对候选人的要求"，公司福利、岗位介绍不算要求，不要输出；
            3. 软性要求（沟通好/抗压）若原文强调可收录，skill 用 素质-xx 前缀；
            4. 原文没有的内容绝不编造；无法结构化时返回空的 requirements。
            """;

    private final ChatClient chatClient;
    private final LlmAvailability availability;

    public JdParsingService(ChatClient.Builder builder, LlmAvailability availability) {
        this.chatClient = builder.defaultSystem(SYSTEM_PROMPT).build();
        this.availability = availability;
    }

    /** 解析 JD 文本为要求条目 */
    public JdRequirements parse(String jdText) {
        // 门槛前置：占位 key 不发网络请求（见 LlmAvailability 注释）
        availability.requireChatConfigured("JD 解析",
                "未配置 API Key 时可降级：在请求体 requirements 字段直接提交已结构化的要求条目数组");
        try {
            return chatClient.prompt()
                    .user(u -> u.text("请把下面 JD 抽取为要求条目：\n\n{jd}").param("jd", truncate(jdText)))
                    .call()
                    .entity(JdRequirements.class);
        } catch (Exception e) {
            throw new LlmCallFailedException("JD 解析调用模型失败", e);
        }
    }

    /** 上下文预算护栏：与简历抽取同款截断，JD 一般比简历短，12000 足够 */
    private String truncate(String text) {
        int max = 12000;
        return text.length() <= max ? text : text.substring(0, max) + "\n...(截断)";
    }
}
