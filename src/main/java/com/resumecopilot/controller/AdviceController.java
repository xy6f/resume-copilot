package com.resumecopilot.controller;

import com.resumecopilot.advise.AdviceService;
import com.resumecopilot.entity.RewriteAdvice;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 改写建议状态接口（W4 遗留：采纳率回写）。
 *
 * <p>为什么不挂在 MatchController：类级前缀是 /api/match，契约路径是
 * /api/advice/{id}/status，Spring 的方法级路径不能跳出类级前缀——
 * 独立薄控制器 + 逻辑在 AdviceService，与其余控制器"薄壳委托服务层"的风格一致。</p>
 *
 * <p>错误收口照旧：非法枚举 IllegalArgumentException→400、查无此建议
 * NotFoundException→404，统一 {code,message,hint} 错误体（见 ApiExceptionAdvice）。</p>
 */
@RestController
@RequestMapping("/api/advice")
@RequiredArgsConstructor
public class AdviceController {

    private final AdviceService adviceService;

    /** 请求体：{status}，三态之一（PENDING/ACCEPTED/IGNORED） */
    @Data
    public static class StatusRequest {
        private String status;
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> patchStatus(@PathVariable Long id,
                                                           @RequestBody StatusRequest req) {
        RewriteAdvice advice = adviceService.updateStatus(id, req.getStatus());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("adviceId", advice.getId());
        out.put("reportId", advice.getReportId());
        out.put("status", advice.getStatus());
        return ResponseEntity.ok(out);
    }
}
