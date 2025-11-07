package me.aboullaite.rag.orchestrator.service;

import me.aboullaite.rag.common.dto.DocumentMetadata;
import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.orchestrator.client.RetrieverClient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    private final RetrieverClient retrieverClient;

    public DocumentService(RetrieverClient retrieverClient) {
        this.retrieverClient = retrieverClient;
    }

    public Mono<List<DocumentMetadata>> listDocuments() {
        // Use a wildcard query to retrieve documents (topK set high to get all)
        Query query = new Query("*", Map.of(), 1000);

        return retrieverClient.retrieve(query)
                .map(docs -> docs.stream()
                        .collect(Collectors.groupingBy(doc ->
                                doc.meta().getOrDefault("docId", doc.id())))
                        .entrySet().stream()
                        .map(entry -> {
                            String docId = entry.getKey();
                            var firstDoc = entry.getValue().get(0);
                            String id = firstDoc.id();
                            String source = firstDoc.meta().getOrDefault("source", "");
                            String section = firstDoc.meta().getOrDefault("section", "");
                            int chunkCount = entry.getValue().size();

                            return new DocumentMetadata(id, docId, source, section, chunkCount);
                        })
                        .sorted((a, b) -> a.docId().compareTo(b.docId()))
                        .collect(Collectors.toList()))
                .onErrorResume(ex -> {
                    log.warn("Failed to list documents: {}", ex.getMessage());
                    return Mono.just(List.of());
                });
    }
}