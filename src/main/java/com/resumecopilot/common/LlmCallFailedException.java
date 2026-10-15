package com.resumecopilot.common;

/**
 * LLM 调用失败异常：key 已配置但真实调用出错（网络不可达、余额不足、模型名不对等），
 * 由 {@link ApiExceptionAdvice} 映射为 502 + LLM_CALL_FAILED。
 *
 * <p>与"未配置"区分开：未配置=环境缺 key（503，重试无意义，要走降级路径）；
 * 调用失败=配置了但这次没成（502，可能是临时故障，可重试）。运维/用户看到的处置不同。</p>
 */
public class LlmCallFailedException extends RuntimeException {

    public LlmCallFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
