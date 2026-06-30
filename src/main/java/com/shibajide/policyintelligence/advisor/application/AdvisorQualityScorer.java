package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.BuiltContext;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityFeatures;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPrediction;
import com.shibajide.policyintelligence.ml.application.RetrievalQualityPredictor;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
class AdvisorQualityScorer {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorQualityScorer.class);

    private final RetrievalQualityPredictor qualityPredictor;

    AdvisorQualityScorer(RetrievalQualityPredictor qualityPredictor) {
        this.qualityPredictor = qualityPredictor;
    }

    PredictionStep predict(String question, List<RetrievedChunk> retrieved, BuiltContext context) {
        Instant started = Instant.now();
        RetrievalQualityPrediction prediction = qualityPredictor.predict(features(question, retrieved, context));
        long latencyMs = elapsedMs(started);

        LOGGER.info(
                "Advisor retrieval quality predicted. label={}, probability={}, modelVersion={}",
                prediction.label(),
                prediction.probability(),
                prediction.modelVersion()
        );
        return new PredictionStep(prediction, latencyMs);
    }

    private RetrievalQualityFeatures features(String question, List<RetrievedChunk> retrieved, BuiltContext context) {
        double topSimilarity = retrieved.isEmpty() ? 0 : retrieved.getFirst().similarityScore();
        double avgTop5 = retrieved.stream().limit(5).mapToDouble(RetrievedChunk::similarityScore).average().orElse(0);
        return new RetrievalQualityFeatures(
                topSimilarity,
                avgTop5,
                context.metrics().documentDiversity(),
                context.metrics().usedChunks(),
                question.length()
        );
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

