package com.trustdesk.kb;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Request body of POST /documents/ingest, shaped like the example in API_CONTRACT.md. */
public record KbIngestRequest(
        @NotEmpty(message = "documents must contain at least one document")
        @Size(max = 100, message = "at most 100 documents per request")
        List<@Valid Document> documents) {

    public record Document(
            @NotBlank(message = "doc_id is required")
            @Pattern(regexp = "[A-Za-z0-9._-]{1,100}",
                     message = "doc_id may only contain letters, digits, '.', '_' and '-' (max 100)")
            String docId,

            @NotBlank(message = "title is required")
            @Size(max = 255, message = "title must be at most 255 characters")
            String title,

            @NotBlank(message = "content is required")
            String content,

            @NotBlank(message = "source_path is required")
            @Size(max = 255, message = "source_path must be at most 255 characters")
            String sourcePath,

            /** Optional; stored as "unspecified" when missing. */
            @Size(max = 255, message = "audience must be at most 255 characters")
            String audience,

            /** Optional; stored as "unspecified" when missing. */
            @Size(max = 255, message = "version must be at most 255 characters")
            String version) {
    }
}