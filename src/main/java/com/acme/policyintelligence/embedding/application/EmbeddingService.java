package com.acme.policyintelligence.embedding.application;

import com.acme.policyintelligence.embedding.infrastructure.ChunkEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int DEFAULT_BATCH_SIZE = 100;

    private final EmbeddingGenerator embeddingGenerator;
    private final ChunkEmbeddingRepository chunkEmbeddingRepository;
    private final int maxBatches;

    public EmbeddingService(
            EmbeddingGenerator embeddingGenerator,
            ChunkEmbeddingRepository chunkEmbeddingRepository,
            @Value("${app.embeddings.max-backfill-batches:100}") int maxBatches
    ) {
        this.embeddingGenerator = embeddingGenerator;
        this.chunkEmbeddingRepository = chunkEmbeddingRepository;
        this.maxBatches = maxBatches;
    }

    @Transactional
    public EmbeddingBackfillResult embedPendingChunks() {
        int completed = 0;
        int failed = 0;
        int batches = 0;

        while (batches < maxBatches) {
            var chunks = chunkEmbeddingRepository.findPendingChunks(DEFAULT_BATCH_SIZE);
            if (chunks.isEmpty()) {
                break;
            }

            batches++;
            for (var chunk : chunks) {
                try {
                    chunkEmbeddingRepository.markCompleted(chunk.id(), embeddingGenerator.embed(chunk.text()));
                    completed++;
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not embed chunk {}", chunk.id(), exception);
                    chunkEmbeddingRepository.markFailed(chunk.id(), exception.getMessage());
                    failed++;
                }
            }
        }
        return new EmbeddingBackfillResult(
                completed,
                failed,
                chunkEmbeddingRepository.countPendingChunks(),
                chunkEmbeddingRepository.countFailedChunks()
        );
    }

    @Transactional
    public EmbeddingBackfillResult retryFailedChunks() {
        int retried = chunkEmbeddingRepository.retryFailedChunks();
        LOGGER.info("Embedding retry requested. resetFailedChunks={}", retried);
        return embedPendingChunks();
    }
}
