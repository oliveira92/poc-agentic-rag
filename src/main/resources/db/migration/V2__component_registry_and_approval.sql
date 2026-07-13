-- ============================================================
-- V2: registro de componentes ingeridos e fila de aprovação (HITL).
-- Estas tabelas são do NOSSO domínio (não do Spring AI).
-- ============================================================

-- Catálogo do que já foi ingerido (idempotência + auditoria da ingestão).
CREATE TABLE IF NOT EXISTS component (
    id            VARCHAR(100) PRIMARY KEY,          -- id do componente no portal
    name          VARCHAR(255) NOT NULL,
    version       VARCHAR(50),
    description   TEXT,
    source_hash   VARCHAR(64),                       -- hash do conteúdo ingerido (evita reingestão)
    last_ingested TIMESTAMP    NOT NULL DEFAULT now()
);

-- Respostas geradas pela LLM aguardando aprovação humana.
-- Só após APPROVED viram chunks na base vetorial (source=llm_approved).
CREATE TABLE IF NOT EXISTS knowledge_approval (
    id             uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    component_id   VARCHAR(100) NOT NULL REFERENCES component (id),
    question       TEXT         NOT NULL,
    answer         TEXT         NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                       CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewer       VARCHAR(255),
    review_note    TEXT,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),
    reviewed_at    TIMESTAMP,
    vector_doc_id  VARCHAR(64)                        -- id do doc no vector_store quando aprovado
);

CREATE INDEX IF NOT EXISTS knowledge_approval_status_idx
    ON knowledge_approval (status);
CREATE INDEX IF NOT EXISTS knowledge_approval_component_idx
    ON knowledge_approval (component_id);
