package com.example.agenticrag.approval;

import com.example.agenticrag.domain.model.ApprovalRecord;
import com.example.agenticrag.domain.model.ApprovalStatus;
import com.example.agenticrag.domain.model.KnowledgeSource;
import com.example.agenticrag.infra.persistence.KnowledgeApprovalRepository;
import com.example.agenticrag.observability.RagMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loop human-in-the-loop (padrão Corrective/Feedback RAG).
 *
 * <p>Só respostas APROVADAS viram conhecimento indexado, com metadado
 * {@code source=LLM_APPROVED} — que tem precedência menor que as fontes primárias.
 * Isso é a salvaguarda contra realimentação/eco do próprio modelo.
 */
@Service
public class KnowledgeApprovalService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeApprovalService.class);

    private final KnowledgeApprovalRepository repository;
    private final VectorStore vectorStore;
    private final RagMetrics metrics;

    public KnowledgeApprovalService(KnowledgeApprovalRepository repository, VectorStore vectorStore, RagMetrics metrics) {
        this.repository = repository;
        this.vectorStore = vectorStore;
        this.metrics = metrics;
    }

    public List<ApprovalRecord> listPending() {
        return repository.findByStatus(ApprovalStatus.PENDING);
    }

    @Transactional
    public ApprovalRecord approve(UUID id, String reviewer, String note) {
        ApprovalRecord record = load(id);
        requirePending(record);

        // Q&A aprovado -> documento indexável na base de conhecimento
        Document doc = Document.builder()
                .text("Pergunta: " + record.question() + "\n\nResposta aprovada: " + record.answer())
                .metadata(Map.of(
                        KnowledgeSource.METADATA_KEY, KnowledgeSource.LLM_APPROVED.name(),
                        "component_id", record.componentId(),
                        "kind", "qa",
                        "approved_by", reviewer == null ? "unknown" : reviewer))
                .build();
        vectorStore.add(List.of(doc));

        repository.markApproved(id, reviewer, note, doc.getId());
        metrics.recordHitl("approved");
        log.info("Aprovação {} indexada (docId={}, componente={}).", id, doc.getId(), record.componentId());
        return load(id);
    }

    public ApprovalRecord reject(UUID id, String reviewer, String note) {
        requirePending(load(id));
        repository.markRejected(id, reviewer, note);
        metrics.recordHitl("rejected");
        return load(id);
    }

    private ApprovalRecord load(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Aprovação não encontrada: " + id));
    }

    private static void requirePending(ApprovalRecord record) {
        if (record.status() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("Aprovação " + record.id() + " já está " + record.status());
        }
    }
}
