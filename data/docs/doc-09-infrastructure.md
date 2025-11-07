# Infrastructure Components

- **Namespaces:** `rag` houses application workloads while `observability` contains the OTEL Collector and Grafana.
- **ConfigMaps:** Provide Spring Boot configuration overlays, OTEL Collector pipeline settings, and Grafana dashboard JSON. Secrets are dev-only and embed demo credentials where required.
- **Persistent Volumes:** Weaviate uses a single PVC for vector index storage. When running locally on KinD, storage is backed by a hostPath provisioner. On GKE, the manifest assumes a standard regional managed disk.
- **Service Mesh (optional):** An Istio `VirtualService` is provided for environments where mutual TLS and traffic policies are mandated. It enforces a 2-second timeout with two retries on the orchestrator endpoint.
- **KServe InferenceService:** Defines a vLLM runtime pointing to an object storage bucket containing model weights. Comments in the manifest explain how to swap to a CPU-only configuration when GPUs are unavailable.
- **Grafana:** Uses an in-repo dashboard definition to visualize pipeline health. Administrators can mount additional dashboards using the same ConfigMap pattern.

All manifests are Kubernetes-native YAML with overlays designed to operate in both local and managed clusters. The `make deploy` target applies them in dependency order so operators can observe the cluster becoming ready step by step.
