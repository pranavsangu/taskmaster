package com.trustdesk.eval;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalCaseRepository extends JpaRepository<EvalCase, String> {

    /** All cases with their ticket loaded, in case order; used by the background runner. */
    @EntityGraph(attributePaths = "ticket")
    List<EvalCase> findAllByOrderByCaseIdAsc();
}
