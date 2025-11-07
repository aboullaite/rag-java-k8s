# Troubleshooting Checklist

1. **Weaviate health checks.** Verify `/v1/.well-known/live` and `/v1/.well-known/ready` respond with HTTP 200. If not, inspect the pod logs for schema or module initialization errors.
2. **Retriever timeouts.** When `rag.retrieval.fallback_total` increases sharply, increase `RETRIEVAL_TIMEOUT_MS` or verify that Weaviate has enough CPU. For local demos, allocating two CPU cores significantly improves response time.
3. **Redis connectivity.** The orchestrator logs a warning when the cache is unavailable. Ensure the Redis service is reachable at `redis.rag.svc.cluster.local:6379` and that no authentication is required in dev environments.
4. **LLM errors.** If the orchestrator frequently emits fallback responses and `rag.fallback.reason` indicates `HttpStatusCodeException`, confirm the KServe InferenceService is ready and the model storage credentials are correct.
5. **Grafana dashboards empty.** Check that Prometheus is scraping the `/actuator/prometheus` endpoints. Missing ServiceMonitor resources are a common root cause.
6. **Traces not appearing.** Confirm the OpenTelemetry Collector is running and exports to Tempo. Verify that the Java agent is mounted in the pod spec and that the `OTEL_EXPORTER_OTLP_ENDPOINT` environment variable matches the collector service address.

Following this checklist helps keep the pipeline healthy across local and cloud environments, ensuring the conference demo runs smoothly even under audience-driven Q&A sessions.
