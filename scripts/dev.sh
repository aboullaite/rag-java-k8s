#!/usr/bin/env bash
set -euo pipefail

CLUSTER_NAME=${CLUSTER_NAME:-rag-demo}
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing dependency: $1" >&2
    exit 1
  fi
}

create_cluster() {
  if kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo "KinD cluster ${CLUSTER_NAME} already exists"
    return
  fi
  cat <<EOF | kind create cluster --name "${CLUSTER_NAME}" --wait 120s --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30000
        hostPort: 30000
      - containerPort: 30001
        hostPort: 30001
EOF
}

install_crds() {
  echo "Installing cert-manager and KServe CRDs"
  kubectl apply --wait=true -f https://github.com/cert-manager/cert-manager/releases/download/v1.13.3/cert-manager.yaml
  echo "Waiting for cert-manager webhook to become ready"
  kubectl wait --namespace cert-manager --for=condition=Ready pods --all --timeout=180s
  kubectl apply --server-side --force-conflicts --wait=true \
    -f https://raw.githubusercontent.com/kserve/kserve/v0.16.0/install/v0.16.0/kserve.yaml
  kubectl apply --server-side --force-conflicts --wait=true \
    -f https://raw.githubusercontent.com/kserve/kserve/v0.16.0/install/v0.16.0/kserve-cluster-resources.yaml
  echo "Installing KEDA"
  kubectl apply --server-side --force-conflicts --wait=true \
    -f https://github.com/kedacore/keda/releases/download/v2.14.0/keda-2.14.0.yaml
}

apply_base() {
  kubectl apply -f "${ROOT_DIR}/deploy/base/namespaces.yaml"
  kubectl apply -f "${ROOT_DIR}/deploy/base/rbac.yaml"
  kubectl apply -f "${ROOT_DIR}/deploy/base/configmap.yaml"
  kubectl apply -f "${ROOT_DIR}/deploy/opensearch-secret.yaml"
}

case "${1:-up}" in
  up)
    require kind
    require kubectl
    create_cluster
    install_crds
    apply_base
    ;;
  down)
    kind delete cluster --name "${CLUSTER_NAME}"
    ;;
  *)
    echo "Usage: $0 [up|down]" >&2
    exit 1
    ;;
esac
