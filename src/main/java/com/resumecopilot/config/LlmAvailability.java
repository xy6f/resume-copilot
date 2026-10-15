package com.resumecopilot.config;

import com.resumecopilot.common.LlmUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * LLM 可用性门控：判断"当前环境是否真的配了可用的 API Key"。
 *
 * <p>为什么用字符串比对而不是捕获 401：占位 key（application.yml 默认值
 * sk-local-dev-placeholder）能让 Spring 上下文正常启动（装配期不炸），但真实调用
 * 必然 401。与其在每个服务里 catch 一串 HTTP 异常再猜原因，不如在调用前
 * 显式判定"这把 key 是不是占位的"，一次判定统一降级——门槛前置，异常路径干净。</p>
 */
@Component
public class LlmAvailability {

    /** 与 application.yml 中 spring.ai.openai.api-key 的默认占位值保持一致 */
    public static final String PLACEHOLDER_KEY = "sk-local-dev-placeholder";

    private final String apiKey;

    public LlmAvailability(@Value("${spring.ai.openai.api-key}") String apiKey) {
        this.apiKey = apiKey;
    }

    /** key 非空且不是占位值才认为"LLM 可用" */
    public boolean chatConfigured() {
        return apiKey != null && !apiKey.isBlank() && !PLACEHOLDER_KEY.equals(apiKey.trim());
    }

    /**
     * 需要真实调用的服务入口统一先调这个：不可用直接抛
     * {@link LlmUnavailableException}（由全局 advice 转 503），绝不让占位 key 打到网络上。
     *
     * @param feature 功能名，写进错误消息方便定位
     * @param hint    给调用方的降级指引（如"请改为直接提交结构化 JSON"）
     */
    public void requireChatConfigured(String feature, String hint) {
        if (!chatConfigured()) {
            throw new LlmUnavailableException(
                    "未配置 OPENAI_API_KEY，功能[" + feature + "]不可用", hint);
        }
    }
}
