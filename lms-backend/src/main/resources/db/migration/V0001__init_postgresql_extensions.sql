-- ============================================================
-- V0001__init_postgresql_extensions.sql
--
-- Enables PostgreSQL extensions required by the LMS schema.
-- This is always the first migration to run on a fresh database.
-- ============================================================

-- pgcrypto — provides gen_random_uuid() and cryptographic helpers.
-- Useful as a server-side fallback for UUID generation and for any
-- DB-level password operations.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- pg_trgm — trigram similarity index.
-- Powers fast LIKE / ILIKE searches on course names, user names, etc.
-- Used by GIN indexes added in later migrations.
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- btree_gin — allows GIN indexes on scalar types (int, text, uuid …).
-- Required to create composite GIN indexes that mix JSONB and regular columns.
CREATE EXTENSION IF NOT EXISTS btree_gin;
