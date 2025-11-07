# Load environment variables from .env if it exists
ifneq (,$(wildcard .env))
    include .env
    export
endif

# Container Registry and GKE Configuration
REGISTRY ?= europe-north1-docker.pkg.dev/mohamed-playground/rag-demo
GCP_PROJECT ?= mohamed-playground
GKE_REGION ?= europe-west4
CLUSTER_NAME ?= rag-demo
GPU_MACHINE_TYPE ?= g2-standard-4
GPU_ACCELERATOR ?= nvidia-l4
GPU_MIN_NODES ?= 0
GPU_MAX_NODES ?= 1

.PHONY: help build test deploy ingest port-forward down clean
.PHONY: gke-cluster gke-gpu gke-credentials gke-deploy gke-expose
.PHONY: dev-up dev-down build-local

# Default target
help:
	@echo "RAG Demo Makefile"
	@echo ""
	@echo "Build & Test:"
	@echo "  build          Build and push images to Artifact Registry"
	@echo "  build-local    Build images locally (KinD only)"
	@echo "  test           Run all tests"
	@echo ""
	@echo "GKE Deployment (europe-west4):"
	@echo "  gke-cluster    Create GKE cluster in europe-west4"
	@echo "  gke-gpu        Create GPU node pool (L4 GPUs)"
	@echo "  gke-credentials Get cluster credentials"
	@echo "  gke-deploy     Deploy all services to GKE"
	@echo "  gke-expose     Expose orchestrator with LoadBalancer"
	@echo ""
	@echo "Local Development (KinD):"
	@echo "  dev-up         Create local KinD cluster"
	@echo "  dev-down       Delete local KinD cluster"
	@echo ""
	@echo "Deployment:"
	@echo "  deploy         Deploy all services"
	@echo "  ingest         Run data ingestion job"
	@echo "  port-forward   Port-forward services locally"
	@echo "  down           Delete all deployed resources"
	@echo "  clean          Clean build artifacts"
	@echo ""
	@echo "Environment Variables:"
	@echo "  REGISTRY       Container registry (default: europe-north1-docker.pkg.dev/mohamed-playground/rag-demo)"
	@echo "  GCP_PROJECT    GCP project (default: mohamed-playground)"
	@echo "  GKE_REGION     GKE region (default: europe-west4)"
	@echo "  CLUSTER_NAME   Cluster name (default: rag-demo)"

# Build targets
build:
	./mvnw -DskipTests clean verify
	./mvnw -pl retriever jib:build --no-transfer-progress
	./mvnw -pl orchestrator jib:build --no-transfer-progress

build-local:
	./mvnw -DskipTests clean verify
	./mvnw -pl retriever jib:dockerBuild --no-transfer-progress
	./mvnw -pl orchestrator jib:dockerBuild --no-transfer-progress

test:
	./mvnw test

clean:
	./mvnw clean

# GKE targets
gke-cluster:
	@echo "Creating GKE cluster in $(GKE_REGION)..."
	gcloud container clusters create $(CLUSTER_NAME) \
	  --project=$(GCP_PROJECT) \
	  --region=$(GKE_REGION) \
	  --num-nodes=2 \
	  --machine-type=n2-standard-4 \
	  --disk-size=50 \
	  --enable-autoscaling \
	  --min-nodes=1 \
	  --max-nodes=4 \
	  --enable-autorepair \
	  --enable-autoupgrade \
	  --enable-ip-alias \
	  --release-channel=regular
	$(MAKE) gke-credentials
	kubectl create namespace rag --dry-run=client -o yaml | kubectl apply -f -
	kubectl create namespace observability --dry-run=client -o yaml | kubectl apply -f -

gke-gpu:
	@echo "Creating GPU node pool in $(GKE_REGION) with $(GPU_MACHINE_TYPE) / $(GPU_ACCELERATOR)..."
	gcloud container node-pools create gpu-pool \
	  --project=$(GCP_PROJECT) \
	  --cluster=$(CLUSTER_NAME) \
	  --region=$(GKE_REGION) \
	  --machine-type=$(GPU_MACHINE_TYPE) \
	  --accelerator=type=$(GPU_ACCELERATOR),count=1,gpu-driver-version=latest \
	  --num-nodes=0 \
	  --min-nodes=$(GPU_MIN_NODES) \
	  --max-nodes=$(GPU_MAX_NODES) \
	  --enable-autoscaling \
	  --enable-autorepair \
	  --enable-autoupgrade \
	  --disk-size=100 \
	  --scopes=cloud-platform

gke-credentials:
	gcloud container clusters get-credentials $(CLUSTER_NAME) \
	  --region=$(GKE_REGION) \
	  --project=$(GCP_PROJECT)

gke-deploy:
	@echo "Deploying to GKE..."
	$(MAKE) deploy
	$(MAKE) gke-expose

gke-expose:
	kubectl apply -f deploy/orchestrator-gke.yaml
	@echo "Waiting for LoadBalancer IP..."
	@kubectl get svc orchestrator-public -n rag -w

# Local dev targets
dev-up:
	./scripts/dev.sh up

dev-down:
	./scripts/dev.sh down

# Deployment targets
deploy:
	kubectl apply -f deploy/base/namespaces.yaml
	kubectl apply -f deploy/base/rbac.yaml
	kubectl apply -f deploy/weaviate.yaml
	kubectl apply -f deploy/opensearch.yaml
	kubectl apply -f deploy/redis.yaml
	kubectl apply -f deploy/vllm-runtime.yaml
	kubectl apply -f deploy/kserve-vllm.yaml
	kubectl apply -f deploy/retriever.yaml
	kubectl apply -f deploy/orchestrator.yaml
	kubectl apply -f deploy/hpa-retriever.yaml
	kubectl apply -f deploy/otel-collector.yaml
	kubectl apply -f deploy/prometheus.yaml
	kubectl apply -f deploy/tempo.yaml
	kubectl apply -f deploy/grafana.yaml

ingest:
	kubectl create configmap ingest-script --namespace rag --from-file=ingest.py=data/ingest/ingest.py --dry-run=client -o yaml | kubectl apply -f -
	kubectl apply -f deploy/ingest-job.yaml
	kubectl -n rag wait --for=condition=complete job/rag-ingest --timeout=300s
	kubectl -n rag delete job/rag-ingest --ignore-not-found
	kubectl -n rag delete configmap/ingest-script --ignore-not-found

port-forward:
	kubectl -n rag port-forward svc/orchestrator 8080:8080 &
	kubectl -n observability port-forward svc/grafana 3000:3000

down:
	kubectl delete -f deploy/orchestrator-gke.yaml --ignore-not-found
	kubectl delete -f deploy/grafana.yaml --ignore-not-found
	kubectl delete -f deploy/tempo.yaml --ignore-not-found
	kubectl delete -f deploy/prometheus.yaml --ignore-not-found
	kubectl delete -f deploy/otel-collector.yaml --ignore-not-found
	kubectl delete -f deploy/hpa-retriever.yaml --ignore-not-found
	kubectl delete -f deploy/orchestrator.yaml --ignore-not-found
	kubectl delete -f deploy/retriever.yaml --ignore-not-found
	kubectl delete -f deploy/kserve-vllm.yaml --ignore-not-found
	kubectl delete -f deploy/vllm-runtime.yaml --ignore-not-found
	kubectl delete -f deploy/redis.yaml --ignore-not-found
	kubectl delete -f deploy/opensearch.yaml --ignore-not-found
	kubectl delete -f deploy/weaviate.yaml --ignore-not-found
	kubectl delete -f deploy/ingest-job.yaml --ignore-not-found
