package me.aboullaite.rag.retriever.web;

import me.aboullaite.rag.common.dto.Query;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.retriever.service.RetrieverService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping(path = "/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class RetrieveController {

    private final RetrieverService retrieverService;

    public RetrieveController(RetrieverService retrieverService) {
        this.retrieverService = retrieverService;
    }

    @PostMapping(path = "/retrieve", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<List<RetrievedDoc>> retrieve(@Valid @RequestBody Mono<Query> queryMono) {
        return queryMono.flatMap(retrieverService::retrieve);
    }
}
