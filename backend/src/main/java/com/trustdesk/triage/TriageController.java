package com.trustdesk.triage;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TriageController {

    private final TriageService triageService;

    public TriageController(TriageService triageService) {
        this.triageService = triageService;
    }

    /** Runs a new triage and stores it. */
    @PostMapping("/tickets/{ticketId}/triage")
    public TriageResponse triage(@PathVariable String ticketId) {
        return triageService.triage(ticketId);
    }

    /** Returns the most recent stored triage without calling the AI again. */
    @GetMapping("/tickets/{ticketId}/triage")
    public TriageResponse latestTriage(@PathVariable String ticketId) {
        return triageService.latestTriage(ticketId);
    }
}
