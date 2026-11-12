package com.trustdesk.draft;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DraftController {

    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    /** Generates a new draft reply and stores it. */
    @PostMapping("/tickets/{ticketId}/draft-reply")
    public DraftResponse draft(@PathVariable String ticketId) {
        return draftService.draft(ticketId);
    }

    /** Returns the most recent stored draft without calling the AI again. */
    @GetMapping("/tickets/{ticketId}/draft-reply")
    public DraftResponse latestDraft(@PathVariable String ticketId) {
        return draftService.latestDraft(ticketId);
    }
}