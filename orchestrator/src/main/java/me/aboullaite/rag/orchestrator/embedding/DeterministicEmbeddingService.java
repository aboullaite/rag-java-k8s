package me.aboullaite.rag.orchestrator.embedding;

import me.aboullaite.rag.common.embedding.DeterministicEmbedding;
import org.springframework.stereotype.Component;

@Component
public class DeterministicEmbeddingService implements EmbeddingService {

    @Override
    public double[] embed(String text) {
        return DeterministicEmbedding.embed(text);
    }
}
