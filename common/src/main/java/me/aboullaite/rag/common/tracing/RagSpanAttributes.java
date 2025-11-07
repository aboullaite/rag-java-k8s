package me.aboullaite.rag.common.tracing;

public final class RagSpanAttributes {

    private RagSpanAttributes() {
    }

    public static final String RETRIEVAL_COUNT = "rag.retrieval.count";
    public static final String RETRIEVED_DOC_IDS = "rag.docs.ids";
    public static final String CACHE_HIT = "rag.cache.hit";
    public static final String GENERATION_MODEL = "gen_ai.model";
    public static final String GENERATION_COMPLETION_TOKENS = "gen_ai.completion.tokens";
    public static final String GENERATION_USAGE_TTFT_MS = "gen_ai.usage.ttft_ms";
    public static final String FALLBACK_REASON = "rag.fallback.reason";
}
