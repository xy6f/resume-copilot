package com.resumecopilot.controller;

import com.resumecopilot.config.LlmAvailability;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查端点（GUI 测试反馈新增）。
 *
 * <p>背景：前端连通灯此前用 "GET 一个必不存在的简历 id" 探测——靠 404 判活，
 * 浏览器 console 每次加载都记一条 404 错误噪音。健康检查就该有专门的、
 * 恒 200 的端点。顺带把 LLM 可用性暴露出来，前端顶栏可显示真实模式。</p>
 */
@RestController
public class HealthController {

    private final LlmAvailability llmAvailability;

    public HealthController(LlmAvailability llmAvailability) {
        this.llmAvailability = llmAvailability;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "llmAvailable", llmAvailability.chatConfigured());
    }
}
