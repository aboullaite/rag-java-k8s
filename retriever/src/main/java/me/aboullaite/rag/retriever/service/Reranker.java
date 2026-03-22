package me.aboullaite.rag.retriever.service;

import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.common.embedding.DeterministicEmbedding;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class Reranker {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);

    private final Timer rerankLatency;

    public Reranker(MeterRegistry meterRegistry) {
        this.rerankLatency = Timer.builder("rag_rerank_latency")
                .description("Time spent reranking retrieved documents")
                .register(meterRegistry);
    }

    public Mono<List<RetrievedDoc>> rerank(String query, List<RetrievedDoc> candidates, int topK) {
        return Mono.fromCallable(() -> {
            Timer.Sample sample = Timer.start();
            try {
                double[] queryEmbedding = DeterministicEmbedding.embed(query);

                List<RetrievedDoc> reranked = candidates.stream()
                        .map(doc -> {
                            double[] chunkEmbedding = DeterministicEmbedding.embed(doc.chunk());
                            double similarity = cosineSimilarity(queryEmbedding, chunkEmbedding);
                            return new RetrievedDoc(doc.id(), doc.chunk(), similarity, doc.meta());
                        })
                        .sorted(Comparator.comparingDouble(RetrievedDoc::score).reversed())
                        .limit(topK)
                        .collect(Collectors.toList());

                log.debug("Reranked {} candidates down to {}", candidates.size(), reranked.size());
                return reranked;
            } finally {
                sample.stop(rerankLatency);
            }
        });
    }

    private double cosineSimilarity(double[] a, double[] b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0.0 : dot / denom;
    }
}