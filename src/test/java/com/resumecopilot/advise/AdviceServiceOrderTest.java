package com.resumecopilot.advise;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumecopilot.common.LlmUnavailableException;
import com.resumecopilot.common.NotFoundException;
import com.resumecopilot.config.LlmAvailability;
import com.resumecopilot.config.MatchProperties;
import com.resumecopilot.entity.MatchReportRecord;
import com.resumecopilot.mapper.RewriteAdviceMapper;
import com.resumecopilot.service.MatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * advice 入口校验顺序单测（缺陷修复配套）：GET /api/match/{id}/advice
 * 必须先判"报告存在性"（404），再判 LLM 可用性门（503）。
 *
 * <p>修复前顺序相反：未配 key 的环境里任何 id——包括根本不存在的报告——
 * 都先撞上 503 门，"资源不存在"的 404 语义被完全掩盖。</p>
 */
class AdviceServiceOrderTest {

    /** 未配置 key 的服务：ChatClient.Builder 只被组装、从不被调用 */
    private AdviceService serviceNoKey(MatchService matchService) {
        return service(new LlmAvailability(LlmAvailability.PLACEHOLDER_KEY), matchService);
    }

    private AdviceService service(LlmAvailability availability, MatchService matchService) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new AdviceService(builder, availability, matchService,
                mock(RewriteAdviceMapper.class), new MatchProperties(), new ObjectMapper());
    }

    @Test
    @DisplayName("报告不存在 + 未配 key：应 404（NotFoundException），不再被 503 掩盖")
    void missingReportBeatsLlmGate() {
        MatchService matchService = mock(MatchService.class);
        when(matchService.requireReport(999L)).thenThrow(new NotFoundException("匹配报告不存在: 999"));
        AdviceService svc = serviceNoKey(matchService);

        // 修复前这里抛的是 LlmUnavailableException（门在存在性检查之前）
        assertThrows(NotFoundException.class, () -> svc.streamAdvice(999L));
    }

    @Test
    @DisplayName("报告存在 + 未配 key：仍是 503（LlmUnavailableException），语义不变")
    void existingReportNoKeyStill503() {
        MatchService matchService = mock(MatchService.class);
        MatchReportRecord record = new MatchReportRecord();
        record.setId(5L);
        when(matchService.requireReport(5L)).thenReturn(record);
        AdviceService svc = serviceNoKey(matchService);

        assertThrows(LlmUnavailableException.class, () -> svc.streamAdvice(5L));
    }

    @Test
    @DisplayName("报告不存在 + 已配 key：同样 404（顺序与 key 无关）")
    void missingReportWithKeyThrowsNotFound() {
        MatchService matchService = mock(MatchService.class);
        when(matchService.requireReport(any())).thenThrow(new NotFoundException("匹配报告不存在"));
        AdviceService svc = service(new LlmAvailability("sk-unit-test-real-key"), matchService);

        assertThrows(NotFoundException.class, () -> svc.streamAdvice(7L));
    }
}
