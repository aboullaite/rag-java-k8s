package me.aboullaite.rag.retriever.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(RetrieverProperties.class)
public class RetrieverConfig {

    @Bean
    WebClient weaviateWebClient(WebClient.Builder builder, RetrieverProperties properties) {
        return builder.clone()
                .baseUrl(properties.getWeaviateUrl())
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(cfg -> cfg.defaultCodecs().maxInMemorySize(8 * 1024 * 1024))
                        .build())
                .build();
    }

    @Bean
    WebClient opensearchWebClient(WebClient.Builder builder, RetrieverProperties properties) {
        if (properties.getOpensearchUrl() == null || properties.getOpensearchUrl().isBlank()) {
            return null;
        }
        return builder.clone()
                .baseUrl(properties.getOpensearchUrl())
                .build();
    }
}
