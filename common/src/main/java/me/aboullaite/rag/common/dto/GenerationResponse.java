package me.aboullaite.rag.common.dto;

import java.util.List;
import java.util.Objects;

public record GenerationResponse(
        String answer,
        List<String> citations,
        List<CitationInfo> citationDetails,
        boolean partial,
        ResponseMetadata metadata) {

    public GenerationResponse {
        Objects.requireNonNull(answer, "answer must not be null");
        citations = citations == null ? List.of() : List.copyOf(citations);
        citationDetails = citationDetails == null ? List.of() : List.copyOf(citationDetails);
    }

    // Backward compatibility constructors
    public GenerationResponse(String answer, List<String> citations, boolean partial) {
        this(answer, citations, List.of(), partial, null);
    }

    public GenerationResponse(String answer, List<String> citations, List<CitationInfo> citationDetails, boolean partial) {
        this(answer, citations, citationDetails, partial, null);
    }

    public record ResponseMetadata(
            boolean cacheHit,
            Double cacheSimilarity,
            String retrievalMethod,
            boolean llmFallback) {
    }
}
