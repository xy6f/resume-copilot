package com.resumecopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 简历-JD 匹配助手启动类（简历项目三，Spring AI 版）。
 *
 * <p>W1 链路：上传 PDF -> PDFBox 抽文本 -> LLM 结构化抽取 -> 存库可查。
 * W2 起补评测集与 EvalRunner（本项目灵魂），W3 匹配打分，W4 SSE 改写建议。</p>
 */
@SpringBootApplication
@MapperScan("com.resumecopilot.mapper")
public class ResumeCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResumeCopilotApplication.class, args);
    }
}
