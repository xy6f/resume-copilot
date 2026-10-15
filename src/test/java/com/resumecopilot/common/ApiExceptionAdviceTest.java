package com.resumecopilot.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全局错误体收口单测（缺陷修复配套）：
 * 1. NotFoundException -> 404 + {code:"NOT_FOUND"}（README 错误码约定里 404 一档的落点）；
 * 2. IllegalArgumentException -> 400、LlmUnavailableException -> 503 的既有契约不回退；
 * 3. SSE 特例：Accept 只要 text/event-stream 时，404/503 错误体仍是可解析 JSON
 *    （内容协商绕开消息转换器的分支要在测试里钉死，防止回归成"空 body 的 500"）。
 */
class ApiExceptionAdviceTest {

    private MockMvc mvc;

    /** 校验载体：与 RawResumeRequest 同款 @NotBlank 契约 */
    public static class EchoPayload {
        @NotBlank(message = "rawText 不能为空")
        public String rawText;
    }

    /** 测试用"故障控制器"：每个端点对应一条异常映射路径 */
    @RestController
    @RequestMapping("/fixture")
    static class FaultController {
        @GetMapping("/resume-missing")
        String resumeMissing() {
            throw new NotFoundException("简历记录不存在: 42");
        }

        @GetMapping("/resume-missing-with-hint")
        String resumeMissingWithHint() {
            throw new NotFoundException("简历记录不存在: 42", "请先 POST /api/resumes/raw 录入简历");
        }

        @GetMapping("/bad-request")
        String badRequest() {
            throw new IllegalArgumentException("resumeId 与 jdId 必填");
        }

        @GetMapping("/llm-off")
        String llmOff() {
            throw new LlmUnavailableException("未配置 OPENAI_API_KEY", "请走降级路径");
        }

        /** @Valid 请求体端点：复刻 POST /api/resumes/raw 的 rawText @NotBlank 契约 */
        @PostMapping("/validate")
        String validate(@Valid @RequestBody EchoPayload payload) {
            return payload.rawText;
        }

        /** SSE 形态端点：异常在进入流式前同步抛出（AdviceService 的降级纪律同款形态） */
        @GetMapping(value = "/advice-503", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String advice503() {
            throw new LlmUnavailableException("未配置 OPENAI_API_KEY，功能[改写建议]不可用", "配置 key 后重试");
        }

        @GetMapping(value = "/advice-404", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
        String advice404() {
            throw new NotFoundException("匹配报告不存在: 99");
        }

        /** 复刻 EvalController 的口令闸门：ResponseStatusException(FORBIDDEN) */
        @PostMapping("/eval-forbidden")
        String evalForbidden() {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN,
                    "缺少或错误的 X-Admin-Token（管理口令见配置 resume-copilot.admin-token）");
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new FaultController())
                .setControllerAdvice(new ApiExceptionAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    @DisplayName("查无此资源：404 + NOT_FOUND + message（修复前该路径是 500）")
    void notFoundMapsTo404() throws Exception {
        mvc.perform(get("/fixture/resume-missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("简历记录不存在: 42"));
    }

    @Test
    @DisplayName("404 错误体带 hint 时如实透出指引")
    void notFoundCarriesHint() throws Exception {
        mvc.perform(get("/fixture/resume-missing-with-hint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.hint").value("请先 POST /api/resumes/raw 录入简历"));
    }

    @Test
    @DisplayName("入参错误仍是 400：与 404 语义分离，不得混用")
    void badRequestStill400() throws Exception {
        mvc.perform(get("/fixture/bad-request"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("缺 key 仍是 503 + LLM_NOT_CONFIGURED")
    void llmUnavailableStill503() throws Exception {
        mvc.perform(get("/fixture/llm-off"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LLM_NOT_CONFIGURED"))
                .andExpect(jsonPath("$.hint").value("请走降级路径"));
    }

    @Test
    @DisplayName("@Valid 请求体失败：400 且错误体是统一 {code,message} 形态（修复前落 Spring 默认体）")
    void beanValidationUsesUnifiedBody() throws Exception {
        mvc.perform(post("/fixture/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rawText\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("rawText 不能为空")));
    }

    @Test
    @DisplayName("SSE 请求（Accept 只要 event-stream）：503 状态码 + JSON 错误体都保住")
    void sseAcceptStillGetsJsonErrorBody() throws Exception {
        mvc.perform(get("/fixture/advice-503").header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("LLM_NOT_CONFIGURED")));
    }

    @Test
    @DisplayName("SSE 请求 + 查无报告：404 + NOT_FOUND 错误体（GET /api/match/{id}/advice 的 404 路径）")
    void sseAcceptNotFoundMapsTo404() throws Exception {
        mvc.perform(get("/fixture/advice-404").header("Accept", MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("NOT_FOUND")));
    }

    @Test
    @DisplayName("ResponseStatusException：403 状态码保住且错误体收口为 {code,message}（修复前落 Spring 默认体）")
    void responseStatusExceptionUsesUnifiedBody() throws Exception {
        mvc.perform(post("/fixture/eval-forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("X-Admin-Token")))
                // 契约收口后不得再出现 Spring 默认体的字段
                .andExpect(jsonPath("$.timestamp").doesNotExist());
    }
}
