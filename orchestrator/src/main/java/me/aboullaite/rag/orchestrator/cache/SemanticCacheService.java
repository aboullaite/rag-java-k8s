package me.aboullaite.rag.orchestrator.cache;

import me.aboullaite.rag.common.dto.GenerationResponse;
import me.aboullaite.rag.common.dto.RetrievedDoc;
import me.aboullaite.rag.orchestrator.config.OrchestratorProperties;
import me.aboullaite.rag.orchestrator.util.SimilarityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SemanticCacheService {

    private static final Logger log = LoggerFactory.getLogger(SemanticCacheService.class);
    private static final String CACHE_KEY_PREFIX = "rag:cache:";
    private static final String CACHE_INDEX_KEY = "rag:cache:index";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OrchestratorProperties properties;

    public SemanticCacheService(
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            OrchestratorProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Mono<CacheHit> lookup(String prompt, double[] embedding) {
        String normalized = normalize(prompt);
        return redisTemplate.opsForSet()
                .members(CACHE_INDEX_KEY)
                .flatMap(key -> loadEntry(key)
                        .map(entry -> new CacheHit(entry, SimilarityUtils.cosineSimilarity(embedding, entry.embedding))))
                .filter(hit -> hit.similarity >= properties.getCacheSimThreshold())
                .sort((a, b) -> Double.compare(b.similarity, a.similarity))
                .next()
                .doOnNext(hit -> log.debug("Cache hit for prompt '{}', similarity {}", normalized, hit.similarity))
                .switchIfEmpty(Mono.empty());
    }

    public Mono<Void> put(String prompt, double[] embedding, GenerationResponse response, List<RetrievedDoc> docs) {
        CacheEntry entry = new CacheEntry(
                normalize(prompt),
                embedding,
                response.answer(),
                response.citations(),
                docs.stream().map(RetrievedDoc::id).toList(),
                System.currentTimeMillis());
        try {
            String json = objectMapper.writeValueAsString(entry);
            String cacheKey = buildKey(entry.normalizedQuery());
            Duration ttl = Duration.ofSeconds(properties.getCacheTtlSeconds());
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, ttl)
                    .flatMap(success -> success
                            ? redisTemplate.opsForSet().add(CACHE_INDEX_KEY, cacheKey)
                            : Mono.just(0L))
                    .then();
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    private Mono<CacheEntry> loadEntry(String key) {
        return redisTemplate.opsForValue().get(key)
                .flatMap(json -> {
                    try {
                        return Mono.just(objectMapper.readValue(json, CacheEntry.class));
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to deserialize cache entry: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    private String normalize(String prompt) {
        return prompt == null ? "" : prompt.trim().toLowerCase(Locale.ROOT);
    }

    private String buildKey(String normalizedPrompt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalizedPrompt.getBytes());
            return CACHE_KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unable to build cache key", e);
        }
    }

    public record CacheEntry(
            String normalizedQuery,
            double[] embedding,
            String answer,
            List<String> citations,
            List<String> docIds,
            long createdAtMillis) {
    }

    public record CacheHit(CacheEntry entry, double similarity) {
    }
}
