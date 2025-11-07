package me.aboullaite.rag.common.dto;

import java.util.Objects;

public record DocumentMetadata(
        String id,
        String docId,
        String source,
        String section,
        int chunkCount) {

    public DocumentMetadata {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(docId, "docId must not be null");
    }

    public DocumentMetadata(String id, String docId, String source, String section) {
        this(id, docId, source, section, 1);
    }
}