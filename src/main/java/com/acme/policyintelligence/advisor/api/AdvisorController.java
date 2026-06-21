package com.acme.policyintelligence.advisor.api;

import com.acme.policyintelligence.advisor.application.AdvisorAnswer;
import com.acme.policyintelligence.advisor.application.AdvisorRequest;
import com.acme.policyintelligence.advisor.application.AdvisorService;
import org.springframework.http.MediaType;
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

    public AdvisorController(AdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @PostMapping
    public AdvisorAnswer answer(@RequestBody AdvisorRequest request) {
        return advisorService.answer(request.question());
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody AdvisorRequest request) {
        var emitter = new SseEmitter(120_000L);
        Thread.startVirtualThread(() -> {
            try {
                var answer = advisorService.answer(request.question(), event -> {
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
}
