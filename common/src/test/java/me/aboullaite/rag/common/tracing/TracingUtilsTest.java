package me.aboullaite.rag.common.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TracingUtilsTest {

    private static InMemorySpanExporter exporter;
    private static SdkTracerProvider tracerProvider;
    private static Tracer tracer;

    @BeforeAll
    static void setupProvider() {
        exporter = InMemorySpanExporter.create();
        tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        tracer = tracerProvider.tracerBuilder("test").build();
    }

    @BeforeEach
    void resetExporter() {
        exporter.reset();
    }

    @AfterEach
    void shutdown() {
        exporter.reset();
    }

    @Test
    void recordRetrievedDocsEnrichesSpan() {
        Span span = tracer.spanBuilder("retrieve").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            TracingUtils.recordRetrievedDocs(span, List.of(
                    new RetrievedDoc("doc-1", "chunk", 0.9d, null),
                    new RetrievedDoc("doc-2", "chunk", 0.8d, null)
            ));
        } finally {
            span.end();
        }

        SpanData data = exporter.getFinishedSpanItems().getFirst();
        assertThat(data.getAttributes().get(AttributeKey.longKey(RagSpanAttributes.RETRIEVAL_COUNT))).isEqualTo(2L);
        assertThat(data.getAttributes().get(AttributeKey.stringKey(RagSpanAttributes.RETRIEVED_DOC_IDS))).isEqualTo("doc-1,doc-2");
    }

    @Test
    void recordModelUsageSetsModelMetadata() {
        Span span = tracer.spanBuilder("generate").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            TracingUtils.recordModelUsage(span, "gemma2", 123, 45);
        } finally {
            span.end();
        }

        SpanData data = exporter.getFinishedSpanItems().getFirst();
        assertThat(data.getAttributes().get(AttributeKey.stringKey(RagSpanAttributes.GENERATION_MODEL))).isEqualTo("gemma2");
        assertThat(data.getAttributes().get(AttributeKey.longKey(RagSpanAttributes.GENERATION_USAGE_TTFT_MS))).isEqualTo(123L);
        assertThat(data.getAttributes().get(AttributeKey.longKey(RagSpanAttributes.GENERATION_COMPLETION_TOKENS))).isEqualTo(45L);
    }

    @Test
    void recordCacheHitAndFallback() {
        Span span = tracer.spanBuilder("ask").startSpan();
        try (Scope ignored = span.makeCurrent()) {
            TracingUtils.recordCacheHit(span, true);
            TracingUtils.recordFallback(span, "opensearch-timeout");
        } finally {
            span.end();
        }

        SpanData data = exporter.getFinishedSpanItems().getFirst();
        assertThat(data.getAttributes().get(AttributeKey.booleanKey(RagSpanAttributes.CACHE_HIT))).isTrue();
        assertThat(data.getAttributes().get(AttributeKey.stringKey(RagSpanAttributes.FALLBACK_REASON))).isEqualTo("opensearch-timeout");
    }
}
