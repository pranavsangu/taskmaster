package com.trustdesk.kb;

/**
 * One row returned by the full-text search query. Spring Data fills in these getters
 * from the query's column aliases (docId, title, snippet, score).
 */
public interface KbSearchHit {

    String getDocId();

    String getTitle();

    String getSnippet();

    double getScore();
}