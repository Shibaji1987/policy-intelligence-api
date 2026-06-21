package com.acme.policyintelligence.embedding.infrastructure;

import com.acme.policyintelligence.embedding.application.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingBackfillRunner implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final EmbeddingService embeddingService;

    public EmbeddingBackfillRunner(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        var result = embeddingService.embedPendingChunks();
        if (result.completed() > 0 || result.failed() > 0) {
            LOGGER.info(
                    "Embedding backfill completed={}, failed={}, remainingPending={}",
                    result.completed(),
                    result.failed(),
                    result.remainingPending()
            );
        }
    }
}
