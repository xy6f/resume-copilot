package com.resumecopilot.common;

/**
 * LLM 未配置异常：OPENAI_API_KEY 缺失（仍是占位 key）时，所有依赖真实模型调用的
 * 接口在进入调用前抛出本异常，由 {@link ApiExceptionAdvice} 统一映射为
 * 503 + 明确错误码 LLM_NOT_CONFIGURED。
 *
 * <p>设计意图（面试可讲）：降级要"响亮地失败"——返回码里带 next-step 提示
 * （比如"请改用结构化 JSON 直接提交"），而不是让用户对着 500 猜原因。</p>
 */
public class LlmUnavailableException extends RuntimeException {

    /** 面向调用方的下一步指引，写进响应体 message */
    private final String hint;

    public LlmUnavailableException(String message, String hint) {
        super(message);
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }
}
