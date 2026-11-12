package com.trustdesk.kb;

import com.trustdesk.common.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores documents sent to POST /documents/ingest. Document IDs are kept exactly as given;
 * an existing ID is updated, not duplicated. The search index updates itself (generated column).
 */
@Service
public class KbIngestService {

    static final String UNSPECIFIED = "unspecified";

    private final KbDocumentRepository kbDocumentRepository;

    public KbIngestService(KbDocumentRepository kbDocumentRepository) {
        this.kbDocumentRepository = kbDocumentRepository;
    }

    @Transactional
    public KbIngestResponse ingest(KbIngestRequest request) {
        Set<String> seen = new HashSet<>();
        for (KbIngestRequest.Document doc : request.documents()) {
            if (!seen.add(doc.docId())) {
                throw new InvalidRequestException("Duplicate doc_id in request: " + doc.docId());
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<String> ids = new ArrayList<>();
        for (KbIngestRequest.Document doc : request.documents()) {
            kbDocumentRepository.save(new KbDocument(
                    doc.docId(),
                    doc.title().strip(),
                    orUnspecified(doc.audience()),
                    orUnspecified(doc.version()),
                    doc.content(),
                    doc.sourcePath().strip(),
                    Checksums.sha256(doc.content()),
                    now));
            ids.add(doc.docId());
        }
        return new KbIngestResponse(ids.size(), ids);
    }

    private static String orUnspecified(String value) {
        return value == null || value.isBlank() ? UNSPECIFIED : value.strip();
    }
}