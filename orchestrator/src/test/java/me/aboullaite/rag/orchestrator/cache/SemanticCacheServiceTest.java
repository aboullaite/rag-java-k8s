package me.aboullaite.rag.orchestrator.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import me.aboullaite.rag.common.dto.GenerationResponse;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.cache.SemanticCacheService.CacheEntry;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveSetOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SemanticCacheServiceTest {

    private static final double[] EMBEDDING = new double[] {1, 0, 0, 0, 0, 0, 0, 0};

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;
    @Mock
    private ReactiveSetOperations<String, String> setOperations;
    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private SemanticCacheService cacheService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setCacheSimThreshold(0.5);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new SemanticCacheService(redisTemplate, objectMapper, props);
    }

    @Test
    void returnsHitWhenSimilarityAboveThreshold() throws Exception {
        CacheEntry entry = new CacheEntry("hello world", EMBEDDING, "cached", List.of("doc-1"), List.of("doc-1"), System.currentTimeMillis());
        when(setOperations.members(any())).thenReturn(Flux.just("rag:cache:key"));
        when(valueOperations.get(any())).thenReturn(Mono.just(objectMapper.writeValueAsString(entry)));

        StepVerifier.create(cacheService.lookup("hello planet", EMBEDDING))
                .assertNext(hit -> {
                    assertThat(hit.similarity()).isGreaterThanOrEqualTo(0.5);
                    assertThat(hit.entry().answer()).isEqualTo("cached");
                })
                .verifyComplete();
    }

    @Test
    void filtersOutBelowThreshold() throws Exception {
        OrchestratorProperties props = new OrchestratorProperties();
        props.setCacheSimThreshold(0.99);
        cacheService = new SemanticCacheService(redisTemplate, objectMapper, props);

        CacheEntry entry = new CacheEntry("hello world", EMBEDDING, "cached", List.of("doc-1"), List.of("doc-1"), System.currentTimeMillis());
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(setOperations.members(any())).thenReturn(Flux.just("rag:cache:key"));
        when(valueOperations.get(any())).thenReturn(Mono.just(objectMapper.writeValueAsString(entry)));

        StepVerifier.create(cacheService.lookup("different text", new double[] {0, 1, 0, 0, 0, 0, 0, 0}))
                .verifyComplete();
    }
}
