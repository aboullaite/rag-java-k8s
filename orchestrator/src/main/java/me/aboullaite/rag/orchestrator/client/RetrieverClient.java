package me.aboullaite.rag.orchestrator.client;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class RetrieverClient {

    private static final Logger log = LoggerFactory.getLogger(RetrieverClient.class);

    private final WebClient retrieverWebClient;
    private final OrchestratorProperties properties;
    private final Tracer tracer;

    public RetrieverClient(@Qualifier("retrieverWebClient") WebClient retrieverWebClient, OrchestratorProperties properties) {
        this.retrieverWebClient = retrieverWebClient;
        this.properties = properties;
        this.tracer = GlobalOpenTelemetry.getTracer("rag-java/orchestrator");
    }

    public Mono<List<RetrievedDoc>> retrieve(Query query) {
        Span span = tracer.spanBuilder("rag.retrieve.invoke")
                .setAttribute("rag.request.topK", query.topK())
                .startSpan();
        return retrieverWebClient.post()
                .uri("/v1/retrieve")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(query)
                .retrieve()
                .bodyToFlux(RetrievedDoc.class)
                .collectList()
                .timeout(Duration.ofMillis(properties.getGenTimeoutMs() / 2))
                .doOnError(span::recordException)
                .doFinally(signal -> span.end());
    }
}
