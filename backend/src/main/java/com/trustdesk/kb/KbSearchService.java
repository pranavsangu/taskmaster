package com.trustdesk.kb;

import com.trustdesk.common.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Finds the knowledge-base documents most relevant to a piece of text.
 * Results are data for the caller to use; document content is never treated as instructions.
 */
@Service
@Transactional(readOnly = true)
public class KbSearchService {

    public static final int DEFAULT_LIMIT = 5;
    static final int MAX_LIMIT = 20;
    static final int MAX_QUERY_LENGTH = 2000;

    private final KbDocumentRepository kbDocumentRepository;

    public KbSearchService(KbDocumentRepository kbDocumentRepository) {
        this.kbDocumentRepository = kbDocumentRepository;
    }

    public KbSearchResponse search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new InvalidRequestException("Query 'q' must not be empty");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException("Query 'q' must be at most " + MAX_QUERY_LENGTH + " characters");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRequestException("'limit' must be between 1 and " + MAX_LIMIT);
        }

        String trimmed = query.strip();
        List<KbSearchResponse.Result> results = kbDocumentRepository.search(trimmed, limit).stream()
                .map(hit -> new KbSearchResponse.Result(hit.getDocId(), hit.getTitle(),
                        oneLine(hit.getSnippet()), round(hit.getScore())))
                .toList();
        return new KbSearchResponse(trimmed, results);
    }

    /** Snippets can span Markdown lines; show them as a single line of text. */
    private static String oneLine(String snippet) {
        return snippet == null ? "" : snippet.replaceAll("\\s+", " ").strip();
    }

    private static double round(double score) {
        return Math.round(score * 10_000) / 10_000.0;
    }
}