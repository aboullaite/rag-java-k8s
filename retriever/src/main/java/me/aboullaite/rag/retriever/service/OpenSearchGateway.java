package me.aboullaite.rag.retriever.service;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class OpenSearchGateway {

    private static final Logger log = LoggerFactory.getLogger(OpenSearchGateway.class);

    private final WebClient opensearchWebClient;
    private final ObjectMapper mapper;

    public OpenSearchGateway(@Qualifier("opensearchWebClient") Optional<WebClient> opensearchWebClient, ObjectMapper mapper) {
        this.opensearchWebClient = opensearchWebClient.orElse(null);
        this.mapper = mapper;
    }

    public boolean isEnabled() {
        return opensearchWebClient != null;
    }

    public Mono<List<RetrievedDoc>> search(Query query, int topK) {
        if (!isEnabled()) {
            return Mono.just(List.of());
        }

        ObjectNode payload = mapper.createObjectNode();
        payload.put("size", topK);
        payload.set("query", buildQueryNode(query));

        return opensearchWebClient.post()
                .uri("/rag-docs/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(this::mapHits)
                .doOnError(ex -> log.warn("OpenSearch fallback failed: {}", ex.getMessage()));
    }

    private JsonNode buildQueryNode(Query query) {
        ObjectNode bool = mapper.createObjectNode();
        bool.set("should", mapper.createArrayNode().add(mapper.createObjectNode()
                .set("multi_match", mapper.createObjectNode()
                        .put("query", query.text())
                        .putArray("fields")
                        .add("chunk^2")
                        .add("section")
                        .add("source"))));
        if (!CollectionUtils.isEmpty(query.filters())) {
            var filtersArray = mapper.createArrayNode();
            query.filters().forEach((key, value) -> {
                ObjectNode termNode = mapper.createObjectNode();
                termNode.set("term", mapper.createObjectNode().put(key + ".keyword", value));
                filtersArray.add(termNode);
            });
            bool.set("filter", filtersArray);
        }
        ObjectNode queryNode = mapper.createObjectNode();
        queryNode.set("bool", bool);
        return queryNode;
    }

    private List<RetrievedDoc> mapHits(JsonNode root) {
        JsonNode hitsNode = root.path("hits").path("hits");
        if (!hitsNode.isArray()) {
            return Collections.emptyList();
        }
        List<ObjectNode> hitList = mapper.convertValue(hitsNode, mapper.getTypeFactory()
                .constructCollectionType(List.class, ObjectNode.class));
        return hitList.stream()
                .map(this::mapHit)
                .collect(Collectors.toList());
    }

    private RetrievedDoc mapHit(ObjectNode hit) {
        ObjectNode source = (ObjectNode) hit.path("_source");
        String id = hit.path("_id").asText();
        String chunk = source.path("chunk").asText("");
        double score = hit.path("_score").asDouble(0.1);
        Map<String, String> meta = new HashMap<>();
        meta.put("source", source.path("source").asText(""));
        meta.put("section", source.path("section").asText(""));
        return new RetrievedDoc(id, chunk, score, meta);
    }
}
