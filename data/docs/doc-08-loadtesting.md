# Load Testing Guide

The repository ships with a K6 script that models a steady stream of 10–30 RPS across distinct prompts. Recommended procedure:

1. Deploy the stack using `make dev-up && make deploy && make ingest`.
2. Port-forward the orchestrator service locally: `make port-forward`.
3. Run the load test with `k6 run scripts/loadtest-k6.js`.
4. Monitor Grafana for scaling events, latency percentiles, and cache hit ratio.
5. Validate that the Horizontal Pod Autoscaler scales the retriever beyond its baseline replica count when sustained RPS surpasses 15.

During the test, observe:

- Semantic cache hits should increase over time as repeated prompts are served.
- The fallback counter remains near zero unless deliberate faults are introduced.
- Token throughput (tokens/sec) correlates with orchestrator throughput; the Grafana dashboard visualizes this relationship.
- Traces captured in Tempo highlight spans for retrieval, fallback, and generation, aiding post-test analysis.
