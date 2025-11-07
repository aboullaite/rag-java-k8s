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
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RetrieverService {

    private static final Logger log = LoggerFactory.getLogger(RetrieverService.class);

    private final WeaviateGateway weaviateGateway;
    private final OpenSearchGateway openSearchGateway;
    private final RetrieverProperties properties;
    private final Timer retrievalLatency;
    private final Counter fallbackCounter;
    private final Tracer tracer;
    private final MeterRegistry meterRegistry;

    public RetrieverService(
            WeaviateGateway weaviateGateway,
            OpenSearchGateway openSearchGateway,
            RetrieverProperties properties,
            MeterRegistry meterRegistry) {
        this.weaviateGateway = weaviateGateway;
        this.openSearchGateway = openSearchGateway;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.retrievalLatency = Timer.builder("rag_retrieval_latency")
                .description("Time spent retrieving documents from vector store")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("rag_retrieval_fallback_total")
                .description("Number of retrievals that used fallback search")
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

        return weaviateGateway.search(query, topK)
                .timeout(Duration.ofMillis(properties.getTimeoutMs()))
                .onErrorResume(throwable -> fallback(query, topK, span, throwable))
                .doOnNext(docs -> TracingUtils.recordRetrievedDocs(span, docs))
                .doOnError(span::recordException)
                .doFinally(signalType -> {
                    sample.stop(retrievalLatency);
                    span.end();
                });
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
