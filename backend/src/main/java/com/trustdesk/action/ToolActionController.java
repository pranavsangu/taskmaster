package com.trustdesk.action;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ToolActionController {

    private final ToolActionService toolActionService;

    public ToolActionController(ToolActionService toolActionService) {
        this.toolActionService = toolActionService;
    }

    /** 201 when a new action is created; 200 when the idempotency key returns an existing one. */
    @PostMapping("/tool-actions")
    public ResponseEntity<ToolActionResponse> request(@Valid @RequestBody CreateToolActionRequest body) {
        ToolActionResponse response = toolActionService.request(body);
        return ResponseEntity.status(response.idempotentReplay() ? HttpStatus.OK : HttpStatus.CREATED).body(response);
    }

    @GetMapping("/tool-actions/{actionId}")
    public ToolActionResponse get(@PathVariable String actionId) {
        return toolActionService.get(actionId);
    }

    @PostMapping("/tool-actions/{actionId}/approve")
    public ToolActionResponse approve(@PathVariable String actionId, @Valid @RequestBody ReviewDecisionRequest body) {
        return toolActionService.approve(actionId, body);
    }

    @PostMapping("/tool-actions/{actionId}/reject")
    public ToolActionResponse reject(@PathVariable String actionId, @Valid @RequestBody ReviewDecisionRequest body) {
        return toolActionService.reject(actionId, body);
    }

    @PostMapping("/tool-actions/{actionId}/execute")
    public ToolActionResponse execute(@PathVariable String actionId) {
        return toolActionService.execute(actionId);
    }

    @GetMapping("/tickets/{ticketId}/tool-actions")
    public List<ToolActionResponse> forTicket(@PathVariable String ticketId) {
        return toolActionService.forTicket(ticketId);
    }
}