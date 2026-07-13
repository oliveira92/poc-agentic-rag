-- ============================================================
-- V1: extensões, vector store (longo prazo) e chat memory (curto prazo)
-- DDL explícito (Spring AI initialize-schema desligado).
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ------------------------------------------------------------
-- MEMÓRIA DE LONGO PRAZO: base de conhecimento vetorial.
-- Estrutura esperada pelo PgVectorStore do Spring AI.
-- dimensão 384 = all-MiniLM-L6-v2 (troca de modelo => nova migração).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS vector_store (
    id        uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content   text,
    metadata  jsonb,
    embedding vector(384)
);

-- Índice ANN HNSW com distância cosseno (estado da arte em pgvector).
CREATE INDEX IF NOT EXISTS vector_store_hnsw_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Índice em metadados para filtragem por componente / fonte (namespacing).
CREATE INDEX IF NOT EXISTS vector_store_metadata_gin_idx
    ON vector_store USING gin (metadata);

-- ------------------------------------------------------------
-- MEMÓRIA DE CURTO PRAZO: histórico de conversa por conversation_id.
-- Estrutura esperada pelo JdbcChatMemoryRepository do Spring AI.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36)  NOT NULL,
    content         TEXT         NOT NULL,
    type            VARCHAR(10)  NOT NULL,
    "timestamp"     TIMESTAMP    NOT NULL,
    CONSTRAINT SPRING_AI_CHAT_MEMORY_TYPE_CHECK
        CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL'))
);

CREATE INDEX IF NOT EXISTS spring_ai_chat_memory_conversation_id_timestamp_idx
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");
