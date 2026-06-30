package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.BuiltContext;
import com.shibajide.policyintelligence.context.application.ContextManager;
import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import com.shibajide.policyintelligence.shared.text.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
class AdvisorContextBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorContextBuilder.class);

    private final ContextManager contextManager;
    private final TokenEstimator tokenEstimator;

    AdvisorContextBuilder(ContextManager contextManager, TokenEstimator tokenEstimator) {
        this.contextManager = contextManager;
        this.tokenEstimator = tokenEstimator;
    }

    ContextStep build(String question, List<RetrievedChunk> retrieved, AdvisorEventSink sink) {
        sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_FILTERING_STARTED, "Context filtering started"));
        Instant started = Instant.now();
        BuiltContext context = contextManager.build(question, retrieved);
        long latencyMs = elapsedMs(started);

        LOGGER.info(
                "Advisor context built. usedChunks={}, discardedChunks={}, estimatedTokens={}, documentDiversity={}",
                context.metrics().usedChunks(),
                context.metrics().discardedChunks(),
                context.metrics().estimatedTokens(),
                context.metrics().documentDiversity()
        );
        sink.emit(AdvisorEvent.of(AdvisorStage.CONTEXT_BUILT, "Context built", eventDetails(question, context, latencyMs)));
        return new ContextStep(context, latencyMs);
    }

    private Map<String, Object> eventDetails(String question, BuiltContext context, long latencyMs) {
        int questionTokens = tokenEstimator.estimate(question);
        var details = new LinkedHashMap<String, Object>();
        details.put("questionTokens", questionTokens);
        details.put("usedChunks", context.metrics().usedChunks());
        details.put("discardedChunks", context.metrics().discardedChunks());
        details.put("estimatedTokens", context.metrics().estimatedTokens());
        details.put("totalEstimatedInputTokens", questionTokens + context.metrics().estimatedTokens());
        details.put("documentDiversity", context.metrics().documentDiversity());
        details.put("duplicateDiscardedChunks", context.metrics().duplicateDiscardedChunks());
        details.put("nearDuplicateDiscardedChunks", context.metrics().nearDuplicateDiscardedChunks());
        details.put("documentQuotaDiscardedChunks", context.metrics().documentQuotaDiscardedChunks());
        details.put("tokenBudgetDiscardedChunks", context.metrics().tokenBudgetDiscardedChunks());
        details.put("maxChunkDiscardedChunks", context.metrics().maxChunkDiscardedChunks());
        details.put("latencyMs", latencyMs);
        return details;
    }

    private long elapsedMs(Instant start) {
        return Duration.between(start, Instant.now()).toMillis();
    }
}

