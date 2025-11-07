package me.aboullaite.rag.common.dto;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record Query(String text, Map<String, String> filters, int topK) {

    public Query {
        Objects.requireNonNull(text, "text must not be null");
        if (text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        filters = filters == null ? Map.of() : Collections.unmodifiableMap(filters);
        if (topK <= 0) {
            topK = 5;
        }
    }
}
