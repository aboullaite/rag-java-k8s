package me.aboullaite.rag.retriever.service;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.common.tracing.TracingUtils;
import me.aboullaite.rag.retriever.config.RetrieverProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RetrieverService {

    private static final Logger log = LoggerFactory.getLogger(RetrieverService.class);
    private static final int RRF_K = 60;

    private final WeaviateGateway weaviateGateway;
    private final OpenSearchGateway openSearchGateway;
    private final Reranker reranker;
    private final RetrieverProperties properties;
    private final Timer retrievalLatency;
    private final Counter fallbackCounter;
    private final Counter hybridCounter;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public RetrieverService(
            WeaviateGateway weaviateGateway,
            OpenSearchGateway openSearchGateway,
            Reranker reranker,
            RetrieverProperties properties,
            MeterRegistry meterRegistry) {
        this.weaviateGateway = weaviateGateway;
        this.openSearchGateway = openSearchGateway;
        this.reranker = reranker;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.retrievalLatency = Timer.builder("rag_retrieval_latency")
                .description("Time spent retrieving documents from vector store")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("rag_retrieval_fallback_total")
                .description("Number of retrievals that used fallback search")
                .register(meterRegistry);
        this.hybridCounter = Counter.builder("rag_retrieval_hybrid_total")
                .description("Number of retrievals that used hybrid search")
                .register(meterRegistry);
        this.tracer = GlobalOpenTelemetry.getTracer("rag-java/retriever");
    }

    public Mono<List<RetrievedDoc>> retrieve(Query query) {
        int topK = query.topK() > 0 ? query.topK() : properties.getTopKDefault();
        return Mono.defer(() -> executeRetrieval(query, topK));
    }

    private Mono<List<RetrievedDoc>> executeRetrieval(Query query, int topK) {
        Span span = tracer.spanBuilder("rag.retrieve")
                .setAttribute("rag.request.topK", topK)
                .setAttribute("rag.request.filters",
                        query.filters() == null ? "" : query.filters().entrySet().stream()
                                .map(entry -> entry.getKey() + ":" + entry.getValue())
                                .collect(Collectors.joining(",")))
                .startSpan();
        Timer.Sample sample = Timer.start(meterRegistry);

        int fetchK = properties.isRerankEnabled() ? properties.getRetrieveK() : topK;

        Mono<List<RetrievedDoc>> retrieval;
        if (properties.isHybridEnabled() && openSearchGateway.isEnabled()) {
            retrieval = executeHybridRetrieval(query, fetchK, span);
        } else {
            retrieval = executeSingleSourceRetrieval(query, fetchK, span);
        }

        Mono<List<RetrievedDoc>> result = retrieval;
        if (properties.isRerankEnabled()) {
            result = result.flatMap(docs -> reranker.rerank(query.text(), docs, topK));
        }

        return result
                .doOnNext(docs -> TracingUtils.recordRetrievedDocs(span, docs))
                .doOnError(span::recordException)
                .doFinally(signalType -> {
                    sample.stop(retrievalLatency);
                    span.end();
                });
    }

    private Mono<List<RetrievedDoc>> executeHybridRetrieval(Query query, int topK, Span span) {
        hybridCounter.increment();

        Mono<List<RetrievedDoc>> vectorMono = weaviateGateway.search(query, topK)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(ex -> {
                    log.warn("Vector search failed in hybrid mode: {}", ex.getMessage());
                    return Mono.just(List.of());
                });

        Mono<List<RetrievedDoc>> lexicalMono = openSearchGateway.search(query, topK)
                .timeout(Duration.ofMillis(500))
                .onErrorResume(ex -> {
                    log.warn("Lexical search failed in hybrid mode: {}", ex.getMessage());
                    return Mono.just(List.of());
                });

        return Mono.zip(vectorMono, lexicalMono)
                .map(tuple -> mergeWithRRF(tuple.getT1(), tuple.getT2(), topK));
    }

    private Mono<List<RetrievedDoc>> executeSingleSourceRetrieval(Query query, int topK, Span span) {
        return weaviateGateway.search(query, topK)
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .onErrorResume(throwable -> fallback(query, topK, span, throwable));
    }

    private List<RetrievedDoc> mergeWithRRF(List<RetrievedDoc> vectorResults, List<RetrievedDoc> lexicalResults, int topK) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievedDoc> docsByKey = new HashMap<>();

        for (int i = 0; i < vectorResults.size(); i++) {
            RetrievedDoc doc = vectorResults.get(i);
            String key = doc.chunk();
            scores.merge(key, 1.0 / (RRF_K + i), Double::sum);
            docsByKey.putIfAbsent(key, doc);
        }

        for (int i = 0; i < lexicalResults.size(); i++) {
            RetrievedDoc doc = lexicalResults.get(i);
            String key = doc.chunk();
            scores.merge(key, 1.0 / (RRF_K + i), Double::sum);
            docsByKey.putIfAbsent(key, doc);
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    RetrievedDoc original = docsByKey.get(entry.getKey());
                    return new RetrievedDoc(original.id(), original.chunk(), entry.getValue(), original.meta());
                })
                .collect(Collectors.toList());
    }

    private Mono<List<RetrievedDoc>> fallback(Query query, int topK, Span parentSpan, Throwable throwable) {
        boolean timeout = throwable instanceof TimeoutException;
        log.warn("Primary vector search failed (timeout={}): {}", timeout, throwable.getMessage());
        fallbackCounter.increment();
        TracingUtils.recordFallback(parentSpan, timeout ? "weaviate-timeout" : throwable.getClass().getSimpleName());
        if (!openSearchGateway.isEnabled()) {
            return Mono.just(List.of());
        }
        return openSearchGateway.search(query, topK)
                .doOnNext(docs -> TracingUtils.recordRetrievedDocs(parentSpan, docs));
    }
}