package com.shibajide.policyintelligence.document.infrastructure;

import com.shibajide.policyintelligence.document.domain.CorpusState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CorpusStateRepository extends JpaRepository<CorpusState, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from CorpusState state where state.tenantId = :tenantId")
    Optional<CorpusState> findLockedByTenantId(String tenantId);
}
