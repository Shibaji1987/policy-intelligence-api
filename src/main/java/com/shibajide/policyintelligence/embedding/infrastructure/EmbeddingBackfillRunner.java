package com.shibajide.policyintelligence.embedding.infrastructure;

import com.shibajide.policyintelligence.embedding.application.EmbeddingService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingBackfillRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingBackfillRunner.class);

    private final EmbeddingService embeddingService;

    public EmbeddingBackfillRunner(EmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @Scheduled(
            initialDelayString = "${app.embeddings.backfill-initial-delay:PT5S}",
            fixedDelayString = "${app.embeddings.backfill-fixed-delay:PT24H}"
    )
    @SchedulerLock(name = "embedding-backfill", lockAtMostFor = "10m", lockAtLeastFor = "30s")
    public void runBackfill() {
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
