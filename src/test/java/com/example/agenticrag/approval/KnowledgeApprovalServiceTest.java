package com.example.agenticrag.approval;

import com.example.agenticrag.domain.model.ApprovalRecord;
import com.example.agenticrag.domain.model.ApprovalStatus;
import com.example.agenticrag.infra.persistence.KnowledgeApprovalRepository;
import com.example.agenticrag.observability.RagMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guarda de qualidade da base (achado real): um rascunho com resposta VAZIA foi aprovado e
 * indexado, envenenando a recuperação — ranqueava em 1º e não entregava nada. Aprovar vazio
 * agora é recusado (409) e nunca chega ao vector store.
 */
class KnowledgeApprovalServiceTest {

    private final KnowledgeApprovalRepository repository = mock(KnowledgeApprovalRepository.class);
    private final VectorStore vectorStore = mock(VectorStore.class);
    private final KnowledgeApprovalService service =
            new KnowledgeApprovalService(repository, vectorStore, new RagMetrics(new SimpleMeterRegistry()));

    private static ApprovalRecord pending(UUID id, String answer) {
        return new ApprovalRecord(id, "payments-sdk", "como implementar em java 25?", answer,
                ApprovalStatus.PENDING, null, null, Instant.now(), null, null);
    }

    @Test
    void approvingEmptyAnswerIsRejectedAndNeverIndexed() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(pending(id, "   ")));

        assertThatThrownBy(() -> service.approve(id, "fernando", "ok"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resposta vazia");

        verify(vectorStore, never()).add(anyList());
    }

    @Test
    void approvingRealAnswerIndexesDocument() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(pending(id, "Use POST /v2/charges com Idempotency-Key.")));

        service.approve(id, "fernando", "validado");

        verify(vectorStore).add(anyList());
    }
}
