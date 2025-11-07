# Data Ingestion Notes

The ingestion job performs the following steps:

1. Waits for Weaviate readiness by polling `/v1/.well-known/ready`.
2. Creates the `Doc` class schema with vectorizer modules enabled. Each document chunk includes `docId`, `chunk`, `source`, and `section` fields.
3. Reads Markdown and text files from `data/docs`. Each file is split into paragraphs capped at ~512 tokens. Paragraphs are normalized (trimming whitespace, collapsing repeated spaces) before ingestion.
4. Generates deterministic embeddings using a SHA-256 based hash so that the demo works without external embedding services. When running with fully-enabled Weaviate modules, the deterministic embedding is ignored in favor of the built-in vectorizer.
5. Upserts each chunk via Weaviate's batch API with idempotent IDs seeded by the file path and chunk index.

The job is safe to re-run: existing objects are overwritten with the same UUIDs, making ingestion idempotent. It also logs a summary of how many files and chunks were processed, along with the average chunk size to help tune future ingestion runs.
