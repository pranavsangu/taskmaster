package com.trustdesk.kb;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final KbSearchService kbSearchService;
    private final KbIngestService kbIngestService;

    public DocumentController(KbSearchService kbSearchService, KbIngestService kbIngestService) {
        this.kbSearchService = kbSearchService;
        this.kbIngestService = kbIngestService;
    }

    @GetMapping("/search")
    public KbSearchResponse search(@RequestParam("q") String query,
                                   @RequestParam(defaultValue = "" + KbSearchService.DEFAULT_LIMIT) int limit) {
        return kbSearchService.search(query, limit);
    }

    @PostMapping("/ingest")
    public KbIngestResponse ingest(@Valid @RequestBody KbIngestRequest request) {
        return kbIngestService.ingest(request);
    }
}
