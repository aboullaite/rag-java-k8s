package me.aboullaite.rag.orchestrator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Configuration
public class WebConfig {

    @Bean
    public RouterFunction<ServerResponse> indexRouter() {
        return RouterFunctions.route(
            GET("/"),
            request -> ok()
                .contentType(MediaType.TEXT_HTML)
                .bodyValue(new ClassPathResource("static/index.html"))
        );
    }
}