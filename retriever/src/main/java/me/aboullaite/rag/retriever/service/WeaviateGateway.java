package me.aboullaite.rag.retriever.service;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.common.embedding.DeterministicEmbedding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WeaviateGateway {

    private static final Logger log = LoggerFactory.getLogger(WeaviateGateway.class);

    private final WebClient weaviateWebClient;
    private final ObjectMapper objectMapper;

    public WeaviateGateway(@Qualifier("weaviateWebClient") WebClient weaviateWebClient, ObjectMapper objectMapper) {
        this.weaviateWebClient = weaviateWebClient;
        this.objectMapper = objectMapper;
    }

    public Mono<List<RetrievedDoc>> search(Query query, int topK) {
        return Mono.fromCallable(() -> buildPayload(query, topK))
                .flatMap(payload -> weaviateWebClient.post()
                        .uri("/v1/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(JsonNode.class))
                .map(this::toDocuments)
                .onErrorResume(ex -> {
                    log.warn("Weaviate query failed: {}", ex.getMessage());
                    return Mono.error(ex);
                });
    }

    private Map<String, Object> buildPayload(Query query, int topK) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("query", buildQuery(query, topK));
        return payload;
    }

    private String buildQuery(Query query, int topK) {
        double[] vector = DeterministicEmbedding.embed(query.text());
        String vectorJson;
        try {
            vectorJson = objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize embedding vector", ex);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{ Get { Doc(limit: ").append(topK);
        builder.append(", nearVector: { vector: ")
                .append(vectorJson)
                .append(" }");

        if (query.filters() != null && !query.filters().isEmpty()) {
            builder.append(", where: { operator: And operands: [");
            String where = query.filters().entrySet().stream()
                    .map(entry -> {
                        try {
                            return "{path:[\"" + entry.getKey() + "\"] operator:Equal valueText:"
                                    + objectMapper.writeValueAsString(entry.getValue()) + "}";
                        } catch (JsonProcessingException ex) {
                            throw new IllegalStateException("Unable to serialize filter value", ex);
                        }
                    })
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            builder.append(where).append("] }");
        }

        builder.append(") { docId chunk source section _additional { id distance } } } }");
        return builder.toString();
    }

    private List<RetrievedDoc> toDocuments(JsonNode root) {
        JsonNode dataNode = root.path("data").path("Get").path("Doc");
        if (!(dataNode instanceof ArrayNode arrayNode)) {
            return List.of();
        }
        List<RetrievedDoc> docs = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            String id = node.path("_additional").path("id").asText(node.path("docId").asText());
            String chunk = node.path("chunk").asText("");
            double score = 1.0 - node.path("_additional").path("distance").asDouble(1.0);
            Map<String, String> meta = new HashMap<>();
            meta.put("source", node.path("source").asText(""));
            meta.put("section", node.path("section").asText(""));
            meta.entrySet().removeIf(entry -> !StringUtils.hasText(entry.getValue()));
            docs.add(new RetrievedDoc(id, chunk, score, meta));
        }
        return docs;
    }
}
