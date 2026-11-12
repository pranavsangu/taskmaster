package com.trustdesk.kb;

import java.util.List;

/** Response of POST /documents/ingest, shaped like the example in API_CONTRACT.md. */
public record KbIngestResponse(int ingested, List<String> documentIds) {
}