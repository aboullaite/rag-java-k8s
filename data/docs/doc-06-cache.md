# Semantic Cache Design

The semantic cache prevents repeated generation for semantically equivalent questions. It stores:

- `normalizedQuery`: lower-cased and trimmed question text with simple PII redaction.
- `embedding`: deterministic 8-dimensional vector generated from the normalized query.
- `answer`: the fully formatted response returned to the client.
- `citations`: the document IDs referenced in the answer.
- `docIds`: the underlying retrieval results that backed the response.
- `createdAtMillis`: timestamp used for observability and optional eviction policies.

Redis Set `rag:cache:index` maintains the keys of cached entries. Lookup iterates over candidates, computing cosine similarity between the incoming query embedding and stored embeddings. A configurable threshold (default 0.90) ensures only highly similar queries reuse cached answers. Metrics report hit and miss counts while OpenTelemetry spans include a `rag.cache.hit` attribute to simplify trace filtering.

Cache entries expire after 10 minutes by default, which keeps answers fresh when underlying documentation changes. Operators can adjust the TTL to trade freshness for cache hit ratio, and the orchestrator provides feature flags to disable caching entirely for debugging.
