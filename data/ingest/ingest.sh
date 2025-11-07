#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v python3 >/dev/null 2>&1; then
  echo "python3 is required to run the ingestion script" >&2
  exit 1
fi

export WEAVIATE_URL="${WEAVIATE_URL:-http://localhost:8080}"
export DOCS_PATH="${DOCS_PATH:-$SCRIPT_DIR/../docs}"

echo "Using Weaviate endpoint: ${WEAVIATE_URL}"
python3 "$SCRIPT_DIR/ingest.py"
