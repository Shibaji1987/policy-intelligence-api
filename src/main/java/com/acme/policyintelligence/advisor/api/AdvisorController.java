package com.acme.policyintelligence.advisor.api;

import com.acme.policyintelligence.advisor.application.AdvisorAnswer;
import com.acme.policyintelligence.advisor.application.AdvisorRequest;
import com.acme.policyintelligence.advisor.application.AdvisorService;
import com.acme.policyintelligence.retrieval.application.RetrievalFilters;
import com.acme.policyintelligence.security.RetrievalAccessPolicy;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/advisor")
public class AdvisorController {

    private final AdvisorService advisorService;
    private final RetrievalAccessPolicy accessPolicy;

    public AdvisorController(AdvisorService advisorService, RetrievalAccessPolicy accessPolicy) {
        this.advisorService = advisorService;
        this.accessPolicy = accessPolicy;
    }

    @PostMapping
    public AdvisorAnswer answer(@RequestBody AdvisorRequest request) {
        return advisorService.answer(request.question(), filters(request));
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AdvisorRequest request) {
        return streamInternal(request);
    }

    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamGet(
            @org.springframework.web.bind.annotation.RequestParam String question,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String tenantId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String department,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String region,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String documentType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String classification
    ) {
        return streamInternal(new AdvisorRequest(question, tenantId, department, region, documentType, classification));
    }

    private SseEmitter streamInternal(AdvisorRequest request) {
        var emitter = new SseEmitter(120_000L);
        Thread.startVirtualThread(() -> {
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
