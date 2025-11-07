# Autoscaling Strategy

Autoscaling combines two complementary mechanisms:

## Horizontal Pod Autoscaler (HPA)
- Target resource: CPU utilization at 70% threshold.
- Minimum replicas: 2 pods for the retriever service to ensure redundancy.
- Maximum replicas: 30 pods to handle bursty traffic during load tests.
- Metrics source: Kubernetes Metrics API via `cpu` resource usage.

## KEDA ScaledObject
- Trigger: Prometheus metrics scraped from the retriever service.
- Query: `sum(rate(http_server_requests_seconds_count{app="retriever"}[1m]))`
- Scaling threshold: 15 requests per second per pod.
- Cooldown period: 120 seconds to avoid oscillation.

By combining HPA and KEDA, the pipeline responds gracefully to both sustained CPU-intensive workloads and sudden traffic spikes. Grafana panels visualize replica counts alongside request latency, enabling operators to correlate scaling events with user-perceived performance. The system favors scale-out during surges and scale-in after a stable idle period, minimizing infrastructure cost without compromising reliability.
