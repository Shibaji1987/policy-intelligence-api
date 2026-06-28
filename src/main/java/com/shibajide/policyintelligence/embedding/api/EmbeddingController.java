package com.shibajide.policyintelligence.embedding.api;

import com.shibajide.policyintelligence.embedding.application.EmbeddingBackfillResult;
import com.shibajide.policyintelligence.embedding.application.EmbeddingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/embeddings")
public class EmbeddingController {

    private final EmbeddingService embeddingService;

    public EmbeddingController(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/backfill")
    public EmbeddingBackfillResult backfill() {
        return embeddingService.embedPendingChunks();
    }

    @PostMapping("/retry-failed")
    public EmbeddingBackfillResult retryFailed() {
        return embeddingService.retryFailedChunks();
    }
}
