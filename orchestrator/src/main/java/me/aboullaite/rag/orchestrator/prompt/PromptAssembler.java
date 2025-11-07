package me.aboullaite.rag.orchestrator.prompt;

import me.aboullaite.rag.common.dto.CitationInfo;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;

@Component
public class PromptAssembler {

    private static final int MAX_CONTEXT_CHARS = 4000;

    private final OrchestratorProperties properties;

    public PromptAssembler(OrchestratorProperties properties) {
        this.properties = properties;
    }

    public PromptBundle assemble(String userPrompt, List<RetrievedDoc> docs) {
        StringBuilder contextBuilder = new StringBuilder();
        List<String> citations = new ArrayList<>();
        List<CitationInfo> citationDetails = new ArrayList<>();
        int remaining = MAX_CONTEXT_CHARS;

        for (RetrievedDoc doc : docs) {
            String section = formatDoc(doc);
            if (section.length() > remaining) {
                break;
            }
            contextBuilder.append(section);
            citations.add(doc.id());

            // Extract metadata for citation
            String docId = doc.meta().getOrDefault("docId", "unknown");
            String source = doc.meta().getOrDefault("source", "unknown");
            String sectionName = doc.meta().getOrDefault("section", "");
            citationDetails.add(new CitationInfo(doc.id(), docId, source, sectionName));

            remaining -= section.length();
        }

        StringBuilder finalPrompt = new StringBuilder();
        finalPrompt.append(properties.getSystemPrompt()).append("\n\n");

        if (contextBuilder.isEmpty()) {
            finalPrompt.append("No supporting documents were retrieved from the knowledge base.\n\n");
            finalPrompt.append("Question: ").append(userPrompt).append("\n\n");
            finalPrompt.append("Since no relevant documents were found in the knowledge base, ");
            finalPrompt.append("respond with: \"I don't know. This information is not available in the knowledge base.\"\n\n");
            finalPrompt.append("Answer:");
        } else {
            finalPrompt.append("Context:\n").append(contextBuilder).append("\n");
            finalPrompt.append("Based on the context above, answer the following question. ");
            finalPrompt.append("Cite sources using their Doc IDs in square brackets.\n\n");
            finalPrompt.append("Question: ").append(userPrompt).append("\n\n");
            finalPrompt.append("Answer:");
        }

        return new PromptBundle(finalPrompt.toString(), citations, citationDetails);
    }

    private String formatDoc(RetrievedDoc doc) {
        StringBuilder builder = new StringBuilder();
        builder.append("Doc ID: ").append(doc.id()).append("\n");
        doc.meta().forEach((k, v) -> builder.append(k).append(": ").append(v).append("\n"));
        builder.append("Content: ").append(doc.chunk()).append("\n---\n");
        return builder.toString();
    }

    public record PromptBundle(String prompt, List<String> citations, List<CitationInfo> citationDetails) {
    }
}
