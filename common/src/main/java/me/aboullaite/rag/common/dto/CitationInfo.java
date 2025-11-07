package me.aboullaite.rag.common.dto;

import java.util.Objects;

public record CitationInfo(String id, String docId, String source, String section) {

    public CitationInfo {
        Objects.requireNonNull(id, "id must not be null");
    }
}