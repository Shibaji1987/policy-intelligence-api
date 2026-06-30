package com.shibajide.policyintelligence.cache.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shibajide.policyintelligence.retrieval.application.RetrievalSearchResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.LongAdder;

@Component
public class RetrievalCache {

    private final Cache<String, RetrievalSearchResponse> localCache;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String backend;
    private final String redisPrefix;
    private final Duration ttl;
    private final LongAdder hits = new LongAdder();
    private final LongAdder misses = new LongAdder();

    public RetrievalCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${app.retrieval.cache.backend:LOCAL}") String backend,
            @Value("${app.retrieval.cache.redis-prefix:policy-intelligence:retrieval:}") String redisPrefix,
            @Value("${app.retrieval.cache.ttl:PT30M}") Duration ttl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.backend = backend.toUpperCase(Locale.ROOT);
        this.redisPrefix = redisPrefix;
        this.ttl = ttl;
        this.localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(ttl)
                .recordStats()
                .build();
    }

    public Optional<RetrievalSearchResponse> get(String key) {
        Optional<RetrievalSearchResponse> response = useRedis()
                ? redisGet(key)
                : Optional.ofNullable(localCache.getIfPresent(key));
        if (response.isPresent()) {
            hits.increment();
        } else {
            misses.increment();
        }
        return response;
    }

    public void put(String key, RetrievalSearchResponse response) {
        if (useRedis()) {
            redisPut(key, response);
        } else {
            localCache.put(key, response);
        }
    }

    public double hitRate() {
        long hitCount = hits.sum();
        long total = hitCount + misses.sum();
        return total == 0 ? 0.0 : (double) hitCount / total;
    }

    public String backend() {
        return useRedis() ? "REDIS" : "LOCAL";
    }

    private boolean useRedis() {
        return "REDIS".equals(backend);
    }

    private Optional<RetrievalSearchResponse> redisGet(String key) {
        String json = redisTemplate.opsForValue().get(redisKey(key));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, RetrievalSearchResponse.class));
        } catch (JsonProcessingException exception) {
            redisTemplate.delete(redisKey(key));
            return Optional.empty();
        }
    }

    private void redisPut(String key, RetrievalSearchResponse response) {
        try {
            redisTemplate.opsForValue().set(redisKey(key), objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException exception) {
            localCache.put(key, response);
        }
    }

    private String redisKey(String key) {
        return redisPrefix + Integer.toHexString(key.hashCode());
    }
}
