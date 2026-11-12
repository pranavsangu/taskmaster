package com.trustdesk.eval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvalRunRepository extends JpaRepository<EvalRun, String> {

    List<EvalRun> findAllByOrderByQueuedAtDesc();
}
