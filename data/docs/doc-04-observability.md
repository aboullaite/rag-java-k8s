# Observability Stack

The observability layer is intentionally lightweight yet comprehensive:

- **Tracing:** The OpenTelemetry Java agent exports spans to the in-cluster OTEL Collector. Custom span attributes capture retrieval counts, document identifiers, model name, token counts, and time-to-first-token metrics.
- **Metrics:** Micrometer exposes Prometheus scrape endpoints on each service. The retriever increments counters for fallback invocations, while the orchestrator records cache hit/miss ratios, generated tokens, and estimated cost per request.
- **Logs:** Structured JSON logs include request IDs and trace correlation IDs so developers can pivot from Grafana panels to raw log context quickly.
- **Dashboards:** Grafana dashboards focus on end-to-end latency percentiles (p50/p95/p99), retrieval hit rates at k, semantic cache efficiency, fallback frequency, TTFT trends, and a synthetic cost-per-request calculation.
- **Alerting hooks:** Prometheus alert rules (sampled in this repository) notify operators if cache hit ratios drop below 20% for ten consecutive minutes or if TTFT exceeds 1.5 seconds.

These capabilities ensure the RAG stack is not a black box. Operators can trace each question through retrieval, reranking, generation, and streaming phases while understanding the cost and reliability implications of each step.
