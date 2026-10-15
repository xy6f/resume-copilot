package com.resumecopilot.advise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.ApiExceptionAdvice;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.controller.AdviceController;
import com.resumecopilot.entity.RewriteAdvice;
import com.resumecopilot.mapper.RewriteAdviceMapper;
import com.resumecopilot.service.MatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 建议采纳率回写（W4 遗留）单测：
 * 服务层钉三态枚举校验与 404 收口，MockMvc 钉 PATCH /api/advice/{id}/status
 * 的路由与统一错误体形态（400 BAD_REQUEST / 404 NOT_FOUND / 200 回显）。
 */
class AdviceStatusWritebackTest {

    private RewriteAdviceMapper mapper = mock(RewriteAdviceMapper.class);

    private AdviceService service() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new AdviceService(builder, new LlmAvailability("sk-unit-test-real-key"),
                mock(MatchService.class), mapper, new MatchProperties(), new ObjectMapper());
    }

    private RewriteAdvice stored(long id) {
        RewriteAdvice a = new RewriteAdvice();
        a.setId(id);
        a.setReportId(9L);
        a.setStatus("PENDING");
        return a;
    }

    @Test
    @DisplayName("合法回写 ACCEPTED：落库状态为大写三态之一")
    void acceptedPersists() {
        when(mapper.selectById(3L)).thenReturn(stored(3L));
        RewriteAdvice out = service().updateStatus(3L, "accepted");
        assertEquals("ACCEPTED", out.getStatus());
        verify(mapper).updateById(any(RewriteAdvice.class));
    }

    @Test
    @DisplayName("非法枚举：IllegalArgumentException（→ 全局 advice 400），绝不触库")
    void illegalEnumRejectedBeforeDb() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service().updateStatus(3L, "ADOPTED"));
        assertEquals(true, e.getMessage().contains("PENDING/ACCEPTED/IGNORED"));
        verify(mapper, never()).updateById(any(RewriteAdvice.class));
    }

    @Test
    @DisplayName("status 缺失/空白：同按非法枚举 400 收口")
    void blankRejected() {
        assertThrows(IllegalArgumentException.class, () -> service().updateStatus(3L, null));
        assertThrows(IllegalArgumentException.class, () -> service().updateStatus(3L, "  "));
    }

    @Test
    @DisplayName("查无此建议：NotFoundException（→ 404 收口风格照旧）")
    void missingAdviceThrowsNotFound() {
        when(mapper.selectById(any())).thenReturn(null);
        assertThrows(NotFoundException.class, () -> service().updateStatus(99L, "IGNORED"));
    }

    private MockMvc mvc(AdviceService svc) {
        return MockMvcBuilders.standaloneSetup(new AdviceController(svc))
                .setControllerAdvice(new ApiExceptionAdvice(new ObjectMapper()))
                .build();
    }

    @Test
    @DisplayName("MockMvc：PATCH 成功 200，回显 adviceId/reportId/status")
    void patchOk() throws Exception {
        when(mapper.selectById(7L)).thenReturn(stored(7L));
        mvc(service()).perform(patch("/api/advice/7/status")
                        .contentType("application/json")
                        .content("{\"status\":\"IGNORED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adviceId").value(7))
                .andExpect(jsonPath("$.reportId").value(9))
                .andExpect(jsonPath("$.status").value("IGNORED"));
    }

    @Test
    @DisplayName("MockMvc：非法枚举 400 + 统一错误体 {code,message}")
    void patchBadEnum() throws Exception {
        mvc(service()).perform(patch("/api/advice/7/status")
                        .contentType("application/json")
                        .content("{\"status\":\"ADOPTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("MockMvc：建议不存在 404 + NOT_FOUND")
    void patchNotFound() throws Exception {
        when(mapper.selectById(any())).thenReturn(null);
        mvc(service()).perform(patch("/api/advice/7/status")
                        .contentType("application/json")
                        .content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
