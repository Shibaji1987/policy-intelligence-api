package com.shibajide.policyintelligence.cache.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievalSearchResponse;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
public class RetrievalCache {

    private final Cache<String, RetrievalSearchResponse> cache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .recordStats()
            .build();

    public Optional<RetrievalSearchResponse> get(String key) {
        return Optional.ofNullable(cache.getIfPresent(key));
    }

    public void put(String key, RetrievalSearchResponse response) {
        cache.put(key, response);
    }

    public double hitRate() {
        return cache.stats().hitRate();
    }
}
