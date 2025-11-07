package me.aboullaite.rag.common.dto;

import java.util.List;
import java.util.Objects;

public record GenerationRequest(String prompt, List<RetrievedDoc> context) {

    public GenerationRequest {
        Objects.requireNonNull(prompt, "prompt must not be null");
        context = context == null ? List.of() : List.copyOf(context);
    }
}
