package com.trustdesk.eval;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EvalController {

    private final EvalService evalService;

    public EvalController(EvalService evalService) {
        this.evalService = evalService;
    }

    /** Starts an eval run in the background: 202 Accepted with status "running". Poll GET /eval-runs/{id}. */
    @PostMapping("/eval-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public EvalRunResponse start(@RequestBody(required = false) CreateEvalRunRequest request) {
        return evalService.start(request);
    }

    @GetMapping("/eval-runs/{evalRunId}")
    public EvalRunResponse get(@PathVariable String evalRunId) {
        return evalService.get(evalRunId);
    }

    /** All eval runs, newest first. */
    @GetMapping("/eval-runs")
    public List<EvalRunResponse> list() {
        return evalService.list();
    }
}
