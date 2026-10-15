package com.resumecopilot.config;

import com.resumecopilot.match.ScoreEngine;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 打分引擎装配：语义通道"缺了就不存在"而不是"存在但报错"。
 *
 * <p>application.yml 默认 spring.ai.model.embedding=none——未配 key 环境里
 * EmbeddingModel bean 根本不会被创建，ObjectProvider.getIfAvailable() 返回 null，
 * ScoreEngine 全程走规则通道并在报告里标注降级。想用 DeepSeek/通义的 embedding
 * 开语义通道：设 SPRING_AI_MODEL_EMBEDDING=openai（或换对应 starter 的模型名）即可，
 * 引擎代码一行不改。装配期降级 vs 运行期 try/catch，前者失败面小得多（面试点）。</p>
 */
@Configuration
public class MatchEngineConfig {

    @Bean
    public ScoreEngine scoreEngine(ObjectProvider<EmbeddingModel> embeddingProvider,
                                   MatchProperties props) {
        return new ScoreEngine(embeddingProvider.getIfAvailable(), props.getSemanticThreshold());
    }
}
