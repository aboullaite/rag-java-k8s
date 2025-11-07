# RAG Java Platform Overview

The RAG Java platform demonstrates how a production-style retrieval augmented generation stack can be assembled with approachable components. It is opinionated around the following principles:

1. **Strong isolation between retrieval, orchestration, and generation.** Each responsibility is a separately deployable Spring Boot microservice with clear contracts and observability.
2. **Deterministic developer workflows.** Local development uses KinD or Minikube so engineers can reproduce the same Kubernetes experience as in CI/CD.
3. **Operational excellence built in.** Health probes, tracing, metrics, dashboards, and autoscaling are enabled from the start rather than added later.

The system is aligned to modern enterprise deployment expectations:

- Runtime: Java 25, Spring Boot 3.3.x, Reactor for non-blocking I/O.
- Vector search: Weaviate for semantic retrieval, with OpenSearch as a lexical fallback.
- Semantic cache: Redis to avoid redundant calls to the LLM and reduce latency.
- LLM serving: KServe with vLLM runtime to host instruction-tuned models.
- Observability: OpenTelemetry, Prometheus, Tempo, and Grafana dashboards.
- Scaling: Kubernetes HPA for CPU scaling and KEDA for event-driven scale-outs.

In practice, the platform behaves like this: incoming questions are sanitized and checked against the semantic cache. Cache misses trigger the retriever service which first attempts vector similarity matches via Weaviate. When latency budgets are exceeded, the pipeline automatically falls back to OpenSearch lexical queries, ensuring a graceful degradation path. The orchestrator service constructs prompts with citations and streams generated tokens back to the client while continuously emitting metrics for latency, cache hit ratio, and cost per call.
