package com.resumecopilot.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常 -> 统一 JSON 错误体 {code, message, hint}。
 *
 * <p>错误码约定（接口契约的一部分，README 同步维护）：
 * LLM_NOT_CONFIGURED=503 缺 key；LLM_CALL_FAILED=502 有 key 但调用失败；
 * BAD_REQUEST=400 入参问题；NOT_FOUND=404 资源不存在。</p>
 *
 * <p>SSE 特例（冒烟抓到的真坑）：GET /api/match/{id}/advice 的请求头是
 * Accept: text/event-stream——若走正常的 ResponseEntity(Map) 路径，内容协商
 * 找不到能把 JSON 写成 event-stream 的转换器，客户端拿到的是空 body 的 500，
 * "明确的 503 + 降级指引"反而丢了。所以对 Accept 明确只要 event-stream 的请求，
 * 错误体直接手写进 response（状态码与 JSON 都保住）；其余请求维持标准 advice。</p>
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class ApiExceptionAdvice {

    private static final String SSE_ACCEPT = "text/event-stream";

    private final ObjectMapper objectMapper;

    /** 缺 key：明确 503，附降级指引 */
    @ExceptionHandler(LlmUnavailableException.class)
    public ResponseEntity<Map<String, Object>> onLlmUnavailable(LlmUnavailableException e,
                                                                HttpServletRequest request,
                                                                HttpServletResponse response) throws IOException {
        log.warn("llm unavailable: {}", e.getMessage());
        Map<String, Object> body = map("LLM_NOT_CONFIGURED", e.getMessage(), e.getHint());
        if (wantsEventStream(request)) {
            writeDirect(response, HttpStatus.SERVICE_UNAVAILABLE, body);
            return null;
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    /** 有 key 但调用失败：502，可重试 */
    @ExceptionHandler(LlmCallFailedException.class)
    public ResponseEntity<Map<String, Object>> onLlmCallFailed(LlmCallFailedException e,
                                                               HttpServletRequest request,
                                                               HttpServletResponse response) throws IOException {
        log.error("llm call failed", e);
        Map<String, Object> body = map("LLM_CALL_FAILED", "模型调用失败：" + rootMessage(e),
                "请检查 OPENAI_API_KEY/OPENAI_BASE_URL 与网络后重试");
        if (wantsEventStream(request)) {
            writeDirect(response, HttpStatus.BAD_GATEWAY, body);
            return null;
        }
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(body);
    }

    /**
     * 资源不存在：404 + 标准错误体。
     * 修复背景（启动验证发现的已知缺陷）：GET /api/resumes/{id} 对不存在的 id
     * 此前抛 IOException（服务层拿"查不到"当 IO 故障上报），全局 advice 无此映射，
     * 落到 Spring 默认处理返回 500——500 是"服务端故障"语义，客户端重试也救不回
     * 一条不存在的记录，状态码撒谎。现统一收口：所有"按 id 查无此资源"
     * （简历/JD/匹配报告）抛 NotFoundException，走本处理器返回 404。
     * SSE 请求同样覆盖：Accept 只要 event-stream 时手写 JSON，避免内容协商丢体。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(NotFoundException e,
                                                          HttpServletRequest request,
                                                          HttpServletResponse response) throws IOException {
        log.warn("resource not found: {}", e.getMessage());
        Map<String, Object> body = map("NOT_FOUND", e.getMessage(), e.getHint());
        if (wantsEventStream(request)) {
            writeDirect(response, HttpStatus.NOT_FOUND, body);
            return null;
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    /** 参数/状态类：400。消息直接透出，方便联调 */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> onBadRequest(RuntimeException e,
                                                            HttpServletRequest request,
                                                            HttpServletResponse response) throws IOException {
        Map<String, Object> body = map("BAD_REQUEST", e.getMessage(), null);
        if (wantsEventStream(request)) {
            writeDirect(response, HttpStatus.BAD_REQUEST, body);
            return null;
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Bean Validation（@Valid 请求体）失败：400 且同样收口进统一错误体。
     * 根因注释（QA 发现）：此前 advice 未覆盖 MethodArgumentNotValidException，
     * POST /api/resumes/raw 空 rawText 等场景落到 Spring 默认错误体
     * {timestamp,status,error,path}——状态码对但形态与契约 {code,message,hint} 不一致，
     * 客户端要按两种结构解析。取第一条字段校验消息透出。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidationFailed(MethodArgumentNotValidException e,
                                                                  HttpServletRequest request,
                                                                  HttpServletResponse response) throws IOException {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .orElse(e.getMessage());
        Map<String, Object> body = map("BAD_REQUEST", msg, null);
        if (wantsEventStream(request)) {
            writeDirect(response, HttpStatus.BAD_REQUEST, body);
            return null;
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * ResponseStatusException（HTTP 语义异常）：按其自带状态码收口进统一错误体。
     * 根因注释（QA 全量测试发现）：POST /api/eval/run 错/缺 X-Admin-Token 时控制器抛
     * ResponseStatusException(FORBIDDEN)——状态码 403 正确，但 advice 没有该类型的分支，
     * 错误体落到 Spring 默认 {timestamp,status,error,path}，与全项目契约
     * {code,message,hint} 不一致（与 MethodArgumentNotValidException 同类问题，一并收口）。
     * code 用状态码的原因短语大写（403→FORBIDDEN、404→NOT_FOUND…），message 透出 reason。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> onResponseStatus(ResponseStatusException e,
                                                                HttpServletRequest request,
                                                                HttpServletResponse response) throws IOException {
        HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
        String code = status.getReasonPhrase().toUpperCase().replace(' ', '_');
        String msg = e.getReason() != null && !e.getReason().isBlank()
                ? e.getReason() : status.getReasonPhrase();
        Map<String, Object> body = map(code, msg, null);
        if (wantsEventStream(request)) {
            writeDirect(response, status, body);
            return null;
        }
        return ResponseEntity.status(status).body(body);
    }

    private Map<String, Object> map(String code, String message, String hint) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", code);
        m.put("message", message);
        if (hint != null && !hint.isBlank()) {
            m.put("hint", hint);
        }
        return m;
    }

    /** Accept 明确排除 JSON、只要 event-stream 才走手写分支（*​/​* 浏览器默认仍走标准 advice） */
    private boolean wantsEventStream(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains(SSE_ACCEPT) && !accept.contains("application/json");
    }

    /** 直接写 response：状态码 + UTF-8 JSON，绕开消息转换器的内容协商 */
    private void writeDirect(HttpServletResponse response, HttpStatus status, Map<String, Object> body)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        response.getWriter().flush();
    }

    private String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage();
    }
}
