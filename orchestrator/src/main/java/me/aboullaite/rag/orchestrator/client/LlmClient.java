package me.aboullaite.rag.orchestrator.client;

import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

    private final WebClient llmWebClient;
    private final OrchestratorProperties properties;
    private final ObjectMapper objectMapper;

    public LlmClient(@Qualifier("llmWebClient") WebClient llmWebClient, OrchestratorProperties properties, ObjectMapper objectMapper) {
        this.llmWebClient = llmWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Mono<LlmResponse> generate(String prompt) {
        Instant start = Instant.now();
        return llmWebClient.post()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(buildPayload(prompt))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .timeout(Duration.ofMillis(properties.getGenTimeoutMs()))
                .map(node -> toResponse(prompt, start, node));
    }

    private Object buildPayload(String prompt) {
        return objectMapper.createObjectNode()
                .put("model", properties.getModelName())
                .put("prompt", prompt)
                .put("temperature", 0.7)
                .put("max_tokens", 512);
    }

    private LlmResponse toResponse(String prompt, Instant start, JsonNode node) {
        // OpenAI-compatible format: choices[0].text
        JsonNode choices = node.path("choices");
        String text = choices.isArray() && choices.size() > 0
                ? choices.get(0).path("text").asText("")
                : "";

        // Fallback to old formats for compatibility
        if (text.isBlank()) {
            JsonNode outputs = node.path("outputs");
            text = outputs.isArray() && outputs.size() > 0
                    ? outputs.get(0).path("text").asText("")
                    : node.path("output_text").asText("");
        }

        if (text.isBlank()) {
            text = "I don't know.";
        }
        long ttft = Duration.between(start, Instant.now()).toMillis();
        int tokens = Math.max(1, text.split("\\s+").length);
        return new LlmResponse(text, ttft, tokens);
    }

    public record LlmResponse(String answer, long ttftMillis, int tokens) {
    }
}
