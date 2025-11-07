package me.aboullaite.rag.orchestrator.web;

import me.aboullaite.rag.common.dto.DocumentMetadata;
import me.aboullaite.rag.common.dto.GenerationResponse;
import me.aboullaite.rag.orchestrator.service.AskService;
import me.aboullaite.rag.orchestrator.service.DocumentService;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class AskController {

    private final AskService askService;
    private final DocumentService documentService;

    public AskController(AskService askService, DocumentService documentService) {
        this.askService = askService;
        this.documentService = documentService;
    }

    @PostMapping(path = "/ask", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<GenerationResponse> ask(@RequestBody AskRequest request) {
        return askService.ask(request.prompt(), request.filters(), request.topK());
    }

    @GetMapping(path = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam("prompt") String prompt,
            @RequestParam(name = "topK", required = false) Integer topK) {
        return askService.ask(prompt, Map.of(), topK)
                .flatMapMany(this::toStream);
    }

    @GetMapping("/documents")
    public Mono<List<DocumentMetadata>> listDocuments() {
        return documentService.listDocuments();
    }

    private Flux<ServerSentEvent<String>> toStream(GenerationResponse response) {
        List<String> tokens = Arrays.asList(response.answer().split("\\s+"));
        Flux<ServerSentEvent<String>> tokenFlux = Flux.fromIterable(tokens)
                .map(token -> ServerSentEvent.<String>builder(token).event("token").build())
                .delayElements(Duration.ofMillis(20));
        ServerSentEvent<String> completion = ServerSentEvent.<String>builder()
                .event("complete")
                .data(String.join(",", response.citations()))
                .comment(response.partial() ? "partial" : "complete")
                .build();
        return tokenFlux.concatWithValues(completion);
    }

    public record AskRequest(String prompt, Map<String, String> filters, Integer topK) {
    }
}
