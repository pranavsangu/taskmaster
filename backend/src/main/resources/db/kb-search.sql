-- Full-text search support for kb_documents.
-- Runs on every startup after Hibernate has created the tables
-- (spring.jpa.defer-datasource-initialization=true), so every statement must be safe to repeat.

-- A word index PostgreSQL keeps up to date automatically whenever title or content changes.
-- Title words get weight A (more important), content words weight B.
ALTER TABLE kb_documents
    ADD COLUMN IF NOT EXISTS search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(content, '')), 'B')
    ) STORED;

-- GIN index: lets PostgreSQL find matching documents without reading every row.
CREATE INDEX IF NOT EXISTS kb_documents_search_idx ON kb_documents USING gin (search_vector);
