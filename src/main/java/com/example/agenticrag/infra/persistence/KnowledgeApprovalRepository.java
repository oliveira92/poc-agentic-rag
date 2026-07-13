package com.example.agenticrag.infra.persistence;

import com.example.agenticrag.domain.model.ApprovalRecord;
import com.example.agenticrag.domain.model.ApprovalStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Fila de aprovação (HITL) das respostas geradas pela LLM. */
@Repository
public class KnowledgeApprovalRepository {

    private final JdbcClient jdbc;

    public KnowledgeApprovalRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID savePending(String componentId, String question, String answer) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO knowledge_approval (id, component_id, question, answer, status, created_at)
                VALUES (:id, :componentId, :question, :answer, 'PENDING', now())
                """)
                .param("id", id)
                .param("componentId", componentId)
                .param("question", question)
                .param("answer", answer)
                .update();
        return id;
    }

    public Optional<ApprovalRecord> findById(UUID id) {
        return jdbc.sql("SELECT * FROM knowledge_approval WHERE id = :id")
                .param("id", id)
                .query(this::map)
                .optional();
    }

    public List<ApprovalRecord> findByStatus(ApprovalStatus status) {
        return jdbc.sql("SELECT * FROM knowledge_approval WHERE status = :status ORDER BY created_at DESC")
                .param("status", status.name())
                .query(this::map)
                .list();
    }

    public void markApproved(UUID id, String reviewer, String note, String vectorDocId) {
        jdbc.sql("""
                UPDATE knowledge_approval
                   SET status = 'APPROVED', reviewer = :reviewer, review_note = :note,
                       reviewed_at = now(), vector_doc_id = :docId
                 WHERE id = :id AND status = 'PENDING'
                """)
                .param("id", id)
                .param("reviewer", reviewer)
                .param("note", note)
                .param("docId", vectorDocId)
                .update();
    }

    public void markRejected(UUID id, String reviewer, String note) {
        jdbc.sql("""
                UPDATE knowledge_approval
                   SET status = 'REJECTED', reviewer = :reviewer, review_note = :note, reviewed_at = now()
                 WHERE id = :id AND status = 'PENDING'
                """)
                .param("id", id)
                .param("reviewer", reviewer)
                .param("note", note)
                .update();
    }

    private ApprovalRecord map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ApprovalRecord(
                UUID.fromString(rs.getString("id")),
                rs.getString("component_id"),
                rs.getString("question"),
                rs.getString("answer"),
                ApprovalStatus.valueOf(rs.getString("status")),
                rs.getString("reviewer"),
                rs.getString("review_note"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("reviewed_at")),
                rs.getString("vector_doc_id"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
