package me.aboullaite.rag.orchestrator.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PromptAssemblerTest {

    private PromptAssembler assembler;

    @BeforeEach
    void setup() {
        OrchestratorProperties properties = new OrchestratorProperties();
        properties.setSystemPrompt("System prompt");
        assembler = new PromptAssembler(properties);
    }

    @Test
    void assemblesPromptWithCitations() {
        RetrievedDoc doc = new RetrievedDoc("doc-1", "Important content about deployment.", 0.9, Map.of("section", "deploy"));
        PromptAssembler.PromptBundle bundle = assembler.assemble("Explain deployment", List.of(doc));

        assertThat(bundle.prompt()).contains("System prompt")
                .contains("Doc ID: doc-1")
                .contains("User question:\nExplain deployment");
        assertThat(bundle.citations()).containsExactly("doc-1");
    }

    @Test
    void enforcesIdkWhenNoContext() {
        PromptAssembler.PromptBundle bundle = assembler.assemble("Explain deployment", List.of());

        assertThat(bundle.prompt()).contains("respond with \"I don't know.\"");
        assertThat(bundle.citations()).isEmpty();
    }
}
