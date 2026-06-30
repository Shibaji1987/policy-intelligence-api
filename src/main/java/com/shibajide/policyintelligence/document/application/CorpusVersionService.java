package com.shibajide.policyintelligence.document.application;

import com.shibajide.policyintelligence.document.domain.CorpusState;
import com.shibajide.policyintelligence.document.infrastructure.CorpusStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorpusVersionService {

    private final CorpusStateRepository corpusStateRepository;

    public CorpusVersionService(CorpusStateRepository corpusStateRepository) {
        this.corpusStateRepository = corpusStateRepository;
    }

    @Transactional
    public long increment(String tenantId) {
        CorpusState state = lockedState(tenantId);
        return state.increment();
    }

    @Transactional(readOnly = true)
    public long currentVersion(String tenantId) {
        return corpusStateRepository.findById(normalizeTenant(tenantId))
                .map(CorpusState::getCorpusVersion)
                .orElse(0L);
    }

    private CorpusState lockedState(String tenantId) {
        String normalizedTenant = normalizeTenant(tenantId);
        return corpusStateRepository.findLockedByTenantId(normalizedTenant)
                .orElseGet(() -> corpusStateRepository.saveAndFlush(new CorpusState(normalizedTenant)));
    }

    private String normalizeTenant(String tenantId) {
        return tenantId == null || tenantId.isBlank() ? "default" : tenantId.strip();
    }
}
