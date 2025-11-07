package me.aboullaite.rag.retriever.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.retriever.config.RetrieverProperties;
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
class RetrieverServiceTest {

    @Mock
    private WeaviateGateway weaviateGateway;

    @Mock
    private OpenSearchGateway openSearchGateway;

    private RetrieverService retrieverService;

    @BeforeEach
    void setup() {
        RetrieverProperties properties = new RetrieverProperties();
        properties.setTimeoutMs(200);
        retrieverService = new RetrieverService(
                weaviateGateway,
                openSearchGateway,
                properties,
                new SimpleMeterRegistry());
    }

    @Test
    void returnsResultsFromWeaviate() {
        List<RetrievedDoc> docs = List.of(
                new RetrievedDoc("doc-1", "chunk", 0.9, Map.of("section", "intro")));
        when(weaviateGateway.search(any(Query.class), anyInt())).thenReturn(Mono.just(docs));

        StepVerifier.create(retrieverService.retrieve(new Query("hello", Map.of(), 3)))
                .expectNextMatches(result -> result.size() == 1 && result.getFirst().id().equals("doc-1"))
                .verifyComplete();
    }

    @Test
    void fallsBackToOpenSearchOnError() {
        List<RetrievedDoc> fallbackDocs = List.of(new RetrievedDoc("lex-1", "chunk", 0.5, Map.of()));
        when(weaviateGateway.search(any(Query.class), anyInt()))
                .thenReturn(Mono.error(new RuntimeException("boom")));
        when(openSearchGateway.isEnabled()).thenReturn(true);
        when(openSearchGateway.search(any(Query.class), anyInt())).thenReturn(Mono.just(fallbackDocs));

        StepVerifier.create(retrieverService.retrieve(new Query("hello", Map.of(), 3)))
                .expectNextMatches(result -> result.size() == 1 && result.getFirst().id().equals("lex-1"))
                .verifyComplete();
    }
}
