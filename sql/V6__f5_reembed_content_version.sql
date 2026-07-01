-- V6 F5 REEMBED content version support
-- Additive migration. Existing chunks start from content_version = 1.
-- Each manual content edit must increment this value.

ALTER TABLE km_document_chunk
  ADD COLUMN IF NOT EXISTS content_version BIGINT NOT NULL DEFAULT 1
  AFTER content;

UPDATE km_document_chunk
SET content_version = 1
WHERE content_version IS NULL
   OR content_version < 1;