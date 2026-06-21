package com.acme.policyintelligence.document.infrastructure;

import com.acme.policyintelligence.document.domain.CorpusState;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface CorpusStateRepository extends JpaRepository<CorpusState, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select state from CorpusState state where state.id = 1")
    CorpusState lockSingleton();
}
