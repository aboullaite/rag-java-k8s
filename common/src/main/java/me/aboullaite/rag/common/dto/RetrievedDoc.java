package me.aboullaite.rag.common.dto;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record RetrievedDoc(String id, String chunk, double score, Map<String, String> meta) {

    public RetrievedDoc {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(chunk, "chunk must not be null");
        meta = meta == null ? Map.of() : Collections.unmodifiableMap(meta);
    }
}
