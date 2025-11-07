package me.aboullaite.rag.orchestrator.embedding;

public interface EmbeddingService {

    double[] embed(String text);
}
