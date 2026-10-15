package com.resumecopilot.common;

/**
 * 资源不存在异常：按 id 查库查不到记录时抛出，由 {@link ApiExceptionAdvice}
 * 统一映射为 404 + 错误体 {code: "NOT_FOUND", message, hint}。
 *
 * <p>为什么单独建一个异常类型而不是复用 IllegalArgumentException：
 * 原实现里"简历不存在"抛 IOException（被当成 IO 故障返回 500）、"JD/报告不存在"
 * 抛 IllegalArgumentException（被当成入参错误返回 400）——三种"查无此资源"
 * 三种状态码，调用方无法按契约处理。404 收口进这一个类型，
 * 语义（资源不存在）与既有 400（参数不合法）彻底分开。</p>
 */
public class NotFoundException extends RuntimeException {

    /** 面向调用方的下一步指引，可为空 */
    private final String hint;

    public NotFoundException(String message) {
        this(message, null);
    }

    public NotFoundException(String message, String hint) {
        super(message);
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }
}
