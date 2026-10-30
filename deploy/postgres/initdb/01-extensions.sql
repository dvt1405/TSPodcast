-- Runs once, when the postgres data volume is empty (docker-entrypoint-initdb.d).
-- Search (plan §2.3/§2.5): websearch_to_tsquery('simple', f_unaccent(q)) + trigram.
-- Schema migrations belong to backend/ (TODO BE-M0), not here.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
