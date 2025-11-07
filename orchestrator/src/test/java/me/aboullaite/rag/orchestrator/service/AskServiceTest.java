package me.aboullaite.rag.orchestrator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import me.aboullaite.rag.common.dto.GenerationResponse;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.cache.SemanticCacheService;
import me.aboullaite.rag.orchestrator.cache.SemanticCacheService.CacheEntry;
import me.aboullaite.rag.orchestrator.cache.SemanticCacheService.CacheHit;
import me.aboullaite.rag.orchestrator.client.LlmClient;
import me.aboullaite.rag.orchestrator.client.LlmClient.LlmResponse;
import me.aboullaite.rag.orchestrator.client.RetrieverClient;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import me.aboullaite.rag.orchestrator.embedding.EmbeddingService;
import me.aboullaite.rag.orchestrator.prompt.PromptAssembler;
import me.aboullaite.rag.orchestrator.prompt.PromptAssembler.PromptBundle;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AskServiceTest {

    @Mock
    private RetrieverClient retrieverClient;
    @Mock
    private LlmClient llmClient;
    @Mock
    private SemanticCacheService cacheService;
    @Mock
    private EmbeddingService embeddingService;
    @Mock
    private PromptAssembler promptAssembler;

    private AskService askService;
    private OrchestratorProperties properties;

    @BeforeEach
    void setup() {
        properties = new OrchestratorProperties();
        properties.setModelName("test-model");
        properties.setSystemPrompt("System");
        when(embeddingService.embed(any())).thenReturn(new double[] {1, 0, 0, 0, 0, 0, 0, 0});
        askService = new AskService(
                retrieverClient,
                llmClient,
                cacheService,
                embeddingService,
                promptAssembler,
                properties,
                new SimpleMeterRegistry());
    }

    @Test
    void returnsCachedResponse() {
        CacheEntry entry = new CacheEntry("prompt", new double[] {1, 0, 0, 0, 0, 0, 0, 0}, "cached-answer", List.of("doc-1"), List.of("doc-1"), System.currentTimeMillis());
        when(cacheService.lookup(any(), any())).thenReturn(Mono.just(new CacheHit(entry, 0.95)));

        StepVerifier.create(askService.ask("prompt", Map.of(), null))
                .assertNext(resp -> assertThat(resp.answer()).isEqualTo("cached-answer"))
                .verifyComplete();
    }

    @Test
    void fallsBackWhenLlmFails() {
        when(cacheService.lookup(any(), any())).thenReturn(Mono.empty());
        List<RetrievedDoc> docs = List.of(new RetrievedDoc("doc-1", "content", 0.9, Map.of()));
        when(retrieverClient.retrieve(any())).thenReturn(Mono.just(docs));
        when(promptAssembler.assemble(any(), any())).thenReturn(new PromptBundle("prompt-with-context", List.of("doc-1"), List.of()));
        when(llmClient.generate(any())).thenReturn(Mono.error(new RuntimeException("llm down")));
        when(cacheService.put(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(askService.ask("prompt", Map.of(), null))
                .assertNext(resp -> {
                    assertThat(resp.partial()).isTrue();
                    assertThat(resp.citations()).contains("doc-1");
                })
                .verifyComplete();
    }

    @Test
    void cachesSuccessfulResponse() {
        when(cacheService.lookup(any(), any())).thenReturn(Mono.empty());
        List<RetrievedDoc> docs = List.of(new RetrievedDoc("doc-1", "content", 0.9, Map.of()));
        when(retrieverClient.retrieve(any())).thenReturn(Mono.just(docs));
        when(promptAssembler.assemble(any(), any())).thenReturn(new PromptBundle("prompt-with-context", List.of("doc-1"), List.of()));
        when(llmClient.generate(any())).thenReturn(Mono.just(new LlmResponse("answer [doc-1]", 10, 4)));
        when(cacheService.put(any(), any(), any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(askService.ask("prompt", Map.of(), null))
                .assertNext(resp -> assertThat(resp.partial()).isFalse())
                .verifyComplete();

        verify(cacheService).put(any(), any(), any(), any());
    }
}
