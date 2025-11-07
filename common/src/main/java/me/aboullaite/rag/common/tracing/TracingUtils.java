package me.aboullaite.rag.common.tracing;

import me.aboullaite.rag.common.dto.RetrievedDoc;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class TracingUtils {

    private TracingUtils() {
    }

    public static void recordRetrievedDocs(Span span, List<RetrievedDoc> docs) {
        if (!span.getSpanContext().isValid() || docs == null) {
            return;
        }
        span.setAttribute(RagSpanAttributes.RETRIEVAL_COUNT, docs.size());
        span.setAttribute(RagSpanAttributes.RETRIEVED_DOC_IDS,
                docs.stream().map(RetrievedDoc::id).collect(Collectors.joining(",")));
    }

    public static void recordCacheHit(Span span, boolean cacheHit) {
        if (!span.getSpanContext().isValid()) {
            return;
        }
        span.setAttribute(RagSpanAttributes.CACHE_HIT, cacheHit);
    }

    public static void recordModelUsage(Span span, String model, long ttftMillis, long completionTokens) {
        if (!span.getSpanContext().isValid()) {
            return;
        }
        if (model != null && !model.isBlank()) {
            span.setAttribute(RagSpanAttributes.GENERATION_MODEL, model);
        }
        if (ttftMillis >= 0) {
            span.setAttribute(RagSpanAttributes.GENERATION_USAGE_TTFT_MS, ttftMillis);
        }
        if (completionTokens >= 0) {
            span.setAttribute(RagSpanAttributes.GENERATION_COMPLETION_TOKENS, completionTokens);
        }
    }

    public static void recordFallback(Span span, String reason) {
        if (!span.getSpanContext().isValid() || Objects.isNull(reason) || reason.isBlank()) {
            return;
        }
        span.setAttribute(RagSpanAttributes.FALLBACK_REASON, reason);
    }

    public static AttributesBuilder newAttributes() {
        return io.opentelemetry.api.common.Attributes.builder();
    }

    public static AttributeKey<String> stringKey(String key) {
        return AttributeKey.stringKey(key);
    }
}
