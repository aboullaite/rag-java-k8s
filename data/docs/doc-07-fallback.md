# Fallback Mechanics

Resilience is critical when the LLM or vector store is overloaded. The demo implements layered fallbacks:

1. **Retriever fallback:** When Weaviate exceeds the retrieval deadline (250 ms), the retriever service automatically issues a lexical query to OpenSearch. It emits `rag.fallback.reason=weaviate-timeout` in the active span so operators can quantify how often the vector path underperforms.
2. **Generator fallback:** If the LLM endpoint returns an error or times out, the orchestrator synthesizes a deterministic answer. It summarizes the top retrieved chunks, clearly labeling the response as partial and citing the contributing document IDs. This keeps the user informed while maintaining reliability.
3. **Streaming resilience:** SSE clients continue to receive heartbeats even during fallback scenarios. The final SSE event includes the list of citations, and the `partial` flag is set to true whenever generation was degraded.

Fallback metrics are surfaced via Prometheus and Grafana so teams can set service level objectives (SLOs) around overall success rate. During load testing, intentionally reducing the retrieval timeout (e.g., to 50 ms) demonstrates how the system degrades gracefully while still responding to >95% of user requests.
