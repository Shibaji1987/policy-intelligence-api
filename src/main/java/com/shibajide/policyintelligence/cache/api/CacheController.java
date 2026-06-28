package com.shibajide.policyintelligence.cache.api;

import com.shibajide.policyintelligence.cache.application.RetrievalCache;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/cache")
public class CacheController {

    private final RetrievalCache retrievalCache;

    public CacheController(RetrievalCache retrievalCache) {
        this.retrievalCache = retrievalCache;
    }

    @GetMapping("/retrieval")
    public Map<String, Object> retrieval() {
        return Map.of("hitRate", retrievalCache.hitRate());
    }
}
