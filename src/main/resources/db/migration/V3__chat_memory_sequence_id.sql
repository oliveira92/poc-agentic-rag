-- ============================================================
-- V3: Spring AI 2.0 adicionou 'sequence_id' em SPRING_AI_CHAT_MEMORY
-- para ordenação determinística das mensagens (antes dependia do timestamp).
-- Como usamos Flyway (initialize-schema: never), a coluna é nossa responsabilidade.
-- DDL alinhado ao schema-postgresql.sql do módulo chat-memory-repository-jdbc 2.0.0.
-- ============================================================

ALTER TABLE SPRING_AI_CHAT_MEMORY ADD COLUMN IF NOT EXISTS sequence_id BIGINT;

-- Backfill determinístico para eventuais linhas já existentes (0-based por conversa).
WITH ordered AS (
    SELECT ctid,
           ROW_NUMBER() OVER (PARTITION BY conversation_id ORDER BY "timestamp") - 1 AS seq
    FROM SPRING_AI_CHAT_MEMORY
)
UPDATE SPRING_AI_CHAT_MEMORY t
   SET sequence_id = o.seq
  FROM ordered o
 WHERE t.ctid = o.ctid
   AND t.sequence_id IS NULL;

ALTER TABLE SPRING_AI_CHAT_MEMORY ALTER COLUMN sequence_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS spring_ai_chat_memory_conversation_id_sequence_id_idx
    ON SPRING_AI_CHAT_MEMORY (conversation_id, sequence_id);
