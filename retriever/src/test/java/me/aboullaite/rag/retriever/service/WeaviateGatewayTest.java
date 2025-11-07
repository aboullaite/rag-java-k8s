package me.aboullaite.rag.retriever.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.aboullaite.rag.common.dto.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class WeaviateGatewayTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;
    private WeaviateGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        WebClient client = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .build();
        gateway = new WeaviateGateway(client, mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void returnsSemanticMatches() throws InterruptedException {
        String responseBody = "{" +
                "\"data\": {" +
                "\"Get\": {" +
                "\"Doc\": [" +
                "{" +
                "\"docId\": \"doc-1\"," +
                "\"chunk\": \"RAG overview\"," +
                "\"source\": \"integration\"," +
                "\"section\": \"overview\"," +
                "\"_additional\": {\"id\": \"doc-1\", \"distance\": 0.1}" +
                "}" +
                "]" +
                "}" +
                "}" +
                "}";
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody));

        Query query = new Query("Explain the platform", Map.of(), 3);

        StepVerifier.create(gateway.search(query, 3))
                .assertNext(result -> {
                    assertThat(result).hasSize(1);
                    assertThat(result.getFirst().id()).isEqualTo("doc-1");
                    assertThat(result.getFirst().chunk()).isEqualTo("RAG overview");
                })
                .verifyComplete();

        RecordedRequest recordedRequest = server.takeRequest();
        String requestBody = recordedRequest.getBody().readUtf8();
        assertThat(requestBody).contains("nearVector");
        assertThat(requestBody).contains("Doc(limit: 3");
    }
}
