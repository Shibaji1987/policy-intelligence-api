package com.shibajide.policyintelligence.advisor.api;

import com.shibajide.policyintelligence.advisor.application.AdvisorAnswer;
import com.shibajide.policyintelligence.advisor.application.AdvisorRequest;
import com.shibajide.policyintelligence.advisor.application.AdvisorService;
import com.shibajide.policyintelligence.advisor.application.AdvisorStreamSessionService;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import com.shibajide.policyintelligence.security.RetrievalAccessPolicy;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/advisor")
@Tag(name = "Advisor", description = "RAG advisor answer generation and streaming trace events")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final AdvisorStreamSessionService streamSessionService;
    private final RetrievalAccessPolicy accessPolicy;
    private final TaskExecutor advisorSseTaskExecutor;

    public AdvisorController(
            AdvisorService advisorService,
            AdvisorStreamSessionService streamSessionService,
            RetrievalAccessPolicy accessPolicy,
            @Qualifier("advisorSseTaskExecutor") TaskExecutor advisorSseTaskExecutor
    ) {
        this.advisorService = advisorService;
        this.streamSessionService = streamSessionService;
        this.accessPolicy = accessPolicy;
        this.advisorSseTaskExecutor = advisorSseTaskExecutor;
    }

    @PostMapping
    @Operation(summary = "Generate a grounded advisor answer")
    public AdvisorAnswer answer(@RequestBody AdvisorRequest request) {
        return advisorService.answer(request.question(), filters(request));
    }

    @PostMapping(path = "/stream")
    @Operation(summary = "Create an advisor stream session")
    public AdvisorStreamSession createStream(@RequestBody AdvisorRequest request) {
        return new AdvisorStreamSession(streamSessionService.create(request));
    }

    @GetMapping(path = "/stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Consume advisor stream events")
    public SseEmitter stream(@PathVariable UUID streamId) {
        return streamInternal(streamSessionService.consume(streamId));
    }

    private SseEmitter streamInternal(AdvisorRequest request) {
        var emitter = new SseEmitter(120_000L);
        advisorSseTaskExecutor.execute(() -> {
            try {
                var answer = advisorService.answer(request.question(), filters(request), event -> {
                    try {
                        emitter.send(SseEmitter.event().name(event.stage().name()).data(event));
                    } catch (IOException exception) {
                        throw new IllegalStateException("Could not send SSE event", exception);
                    }
                });
                emitter.send(SseEmitter.event().name("ANSWER").data(answer));
                emitter.complete();
            } catch (RuntimeException | IOException exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    private RetrievalFilters filters(AdvisorRequest request) {
        return accessPolicy.filters(
                request.tenantId(),
                request.department(),
                request.region(),
                request.documentType(),
                request.classification()
        );
    }
}
