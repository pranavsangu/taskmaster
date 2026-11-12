package com.trustdesk.kb;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface KbDocumentRepository extends JpaRepository<KbDocument, String> {

    /**
     * PostgreSQL full-text search over title and content (the search_vector column from db/kb-search.sql).
     * <ul>
     *   <li>plainto_tsquery turns the text into search terms: lowercase, stemmed ("damaged" → "damag"),
     *       common words like "the" removed. It joins terms with AND (&amp;).</li>
     *   <li>Replacing &amp; with | (OR) means a document matches if it contains ANY of the terms;
     *       documents matching more terms rank higher. This works for long queries such as ticket text.</li>
     *   <li>ts_rank_cd scores relevance; ts_headline cuts out the best-matching passage as a snippet.</li>
     * </ul>
     */
    @Query(value = """
            SELECT d.doc_id AS docId,
                   d.title AS title,
                   ts_headline('english', d.content, q,
                               'MaxWords=35, MinWords=15, StartSel="", StopSel=""') AS snippet,
                   ts_rank_cd(d.search_vector, q) AS score
            FROM kb_documents d,
                 CAST(replace(CAST(plainto_tsquery('english', :query) AS text), '&', '|') AS tsquery) AS q
            WHERE d.search_vector @@ q
            ORDER BY score DESC, d.doc_id
            LIMIT :limit
            """, nativeQuery = true)
    List<KbSearchHit> search(@Param("query") String query, @Param("limit") int limit);
}