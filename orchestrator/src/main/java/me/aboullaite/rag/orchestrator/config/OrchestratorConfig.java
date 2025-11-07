package me.aboullaite.rag.orchestrator.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(OrchestratorProperties.class)
public class OrchestratorConfig {

    @Bean
    WebClient retrieverWebClient(WebClient.Builder builder, OrchestratorProperties properties) {
        return builder.clone()
                .baseUrl(properties.getRetrieverUrl())
                .build();
    }

    @Bean
    WebClient llmWebClient(WebClient.Builder builder, OrchestratorProperties properties) {
        return builder.clone()
                .baseUrl(properties.getLlmUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                        .build())
                .build();
    }

    @Bean
    ReactiveStringRedisTemplate reactiveStringRedisTemplate(ReactiveRedisConnectionFactory factory) {
        return new ReactiveStringRedisTemplate(factory);
    }
}
