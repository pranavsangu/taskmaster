package com.trustdesk.eval;

import java.util.List;

/** Body of POST /eval-runs. Optional: no body, or no case_ids, runs every case. */
public record CreateEvalRunRequest(List<String> caseIds) {
}
