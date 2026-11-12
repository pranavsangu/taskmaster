package com.trustdesk.kb;

import java.util.List;

/** Response of GET /documents/search, shaped like the example in API_CONTRACT.md. */
public record KbSearchResponse(String query, List<Result> results) {

    public record Result(String docId, String title, String snippet, double score) {
    }
}