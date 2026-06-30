package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AdvisorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AdvisorService.class);

    private final QueryRouter queryRouter;
    private final AdvisorWorkflow workflow;
    private final AdvisorNoRetrievalResponder noRetrievalResponder;

    public AdvisorService(
            QueryRouter queryRouter,
            AdvisorWorkflow workflow,
            AdvisorNoRetrievalResponder noRetrievalResponder
    ) {
        this.queryRouter = queryRouter;
        this.workflow = workflow;
        this.noRetrievalResponder = noRetrievalResponder;
    }

    public AdvisorAnswer answer(String question) {
        return answer(question, RetrievalFilters.defaults(), event -> {
        });
    }

    public AdvisorAnswer answer(String question, RetrievalFilters filters) {
        return answer(question, filters, event -> {
        });
    }

    public AdvisorAnswer answer(String question, AdvisorEventSink sink) {
        return answer(question, RetrievalFilters.defaults(), sink);
    }

    public AdvisorAnswer answer(String question, RetrievalFilters filters, AdvisorEventSink sink) {
        try {
            Instant requestStarted = Instant.now();
            LOGGER.info("Advisor request started. questionLength={}", question.length());
            sink.emit(AdvisorEvent.of(AdvisorStage.QUESTION_RECEIVED, "Question received"));

            QueryRoute route = queryRouter.route(question);
            if (!route.retrievalRequired()) {
                return noRetrievalResponder.answer(question, route, requestStarted, sink);
            }
            return workflow.answer(question, filters, requestStarted, sink);
        } catch (RuntimeException exception) {
            LOGGER.warn("Advisor request failed.", exception);
            sink.emit(AdvisorEvent.of(AdvisorStage.ANSWER_FAILED, exception.getMessage()));
            throw exception;
        }
    }
}
