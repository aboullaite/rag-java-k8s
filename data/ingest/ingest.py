#!/usr/bin/env python3
import hashlib
import json
import os
import time
import uuid
from dataclasses import dataclass
from pathlib import Path
from typing import List

import requests

WEAVIATE_URL = os.environ.get("WEAVIATE_URL", "http://localhost:8080")
DOCS_PATH = Path(os.environ.get("DOCS_PATH", Path(__file__).resolve().parent.parent / "docs"))


@dataclass
class Chunk:
    doc_id: str
    source: str
    section: str
    text: str


def wait_for_weaviate(timeout: int = 60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            resp = requests.get(f"{WEAVIATE_URL}/v1/.well-known/ready", timeout=5)
            if resp.status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(2)
    raise RuntimeError("Weaviate did not become ready in time")


def ensure_schema():
    schema = {
        "class": "Doc",
        "description": "Demo knowledge base documents",
        "vectorizer": "none",
        "properties": [
            {"name": "docId", "dataType": ["text"]},
            {"name": "chunk", "dataType": ["text"]},
            {"name": "source", "dataType": ["text"]},
            {"name": "section", "dataType": ["text"]},
        ],
    }
    existing = requests.get(f"{WEAVIATE_URL}/v1/schema/Doc", timeout=5)
    if existing.status_code == 200:
        return
    resp = requests.post(f"{WEAVIATE_URL}/v1/schema", json=schema, timeout=10)
    resp.raise_for_status()


def deterministic_embedding(text: str) -> List[float]:
    digest = hashlib.sha256(text.lower().strip().encode("utf-8")).digest()
    floats = []
    for i in range(0, 32, 4):
        chunk = digest[i : i + 4]
        value = int.from_bytes(chunk, "big", signed=True) / float(2**31 - 1)
        floats.append(value)
    norm = sum(v * v for v in floats) ** 0.5
    return [v / norm if norm else v for v in floats]


def chunk_file(path: Path) -> List[Chunk]:
    content = path.read_text(encoding="utf-8")
    paragraphs = [p.strip() for p in content.split("\n\n") if p.strip()]
    chunks: List[Chunk] = []
    buffer = []
    buffer_len = 0
    section = paragraphs[0][:80] if paragraphs else path.stem

    for para in paragraphs:
        if buffer_len + len(para) > 900:
            chunks.append(Chunk(path.stem, path.name, section, "\n\n".join(buffer)))
            buffer = [para]
            buffer_len = len(para)
        else:
            buffer.append(para)
            buffer_len += len(para)
    if buffer:
        chunks.append(Chunk(path.stem, path.name, section, "\n\n".join(buffer)))
    return chunks


def upsert_chunk(chunk: Chunk, index: int):
    chunk_id = uuid.UUID(hashlib.md5(f"{chunk.doc_id}:{index}".encode("utf-8")).hexdigest())
    payload = {
        "class": "Doc",
        "id": str(chunk_id),
        "properties": {
            "docId": chunk.doc_id,
            "chunk": chunk.text,
            "source": chunk.source,
            "section": chunk.section,
        },
        "vector": deterministic_embedding(chunk.text),
    }
    resp = requests.post(f"{WEAVIATE_URL}/v1/objects", json=payload, timeout=10)
    if resp.status_code >= 400:
        raise RuntimeError(f"Failed to upsert chunk {chunk.doc_id}-{index}: {resp.text}")


def ingest():
    wait_for_weaviate()
    ensure_schema()
    files = sorted(DOCS_PATH.glob("*"))
    total_chunks = 0
    for path in files:
        if not path.is_file():
            continue
        chunks = chunk_file(path)
        for idx, chunk in enumerate(chunks):
            upsert_chunk(chunk, idx)
            total_chunks += 1
        print(f"Ingested {path.name} ({len(chunks)} chunks)")
    print(f"Ingestion complete. Files: {len(files)}, chunks: {total_chunks}")


if __name__ == "__main__":
    ingest()
