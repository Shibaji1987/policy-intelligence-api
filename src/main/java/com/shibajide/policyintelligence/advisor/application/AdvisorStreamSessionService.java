package com.shibajide.policyintelligence.advisor.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AdvisorStreamSessionService {

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Duration sessionTtl;
    private final Clock clock;

    public AdvisorStreamSessionService(
            @Value("${app.advisor.stream-session-ttl:PT5M}") Duration sessionTtl,
            Clock clock
    ) {
        this.sessionTtl = sessionTtl;
        this.clock = clock;
    }

    public UUID create(AdvisorRequest request) {
        evictExpired();
        UUID streamId = UUID.randomUUID();
        sessions.put(streamId, new Session(request, Instant.now(clock).plus(sessionTtl)));
        return streamId;
    }

    public AdvisorRequest consume(UUID streamId) {
        evictExpired();
        return Optional.ofNullable(sessions.remove(streamId))
                .filter(session -> session.expiresAt().isAfter(Instant.now(clock)))
                .map(Session::request)
                .orElseThrow(() -> new IllegalArgumentException("Advisor stream session not found or expired"));
    }

    private void evictExpired() {
        Instant now = Instant.now(clock);
        sessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private record Session(AdvisorRequest request, Instant expiresAt) {
    }
}
