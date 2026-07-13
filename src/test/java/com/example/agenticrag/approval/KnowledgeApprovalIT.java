package com.example.agenticrag.approval;

import com.example.agenticrag.AbstractIntegrationTest;
import com.example.agenticrag.domain.model.ApprovalRecord;
import com.example.agenticrag.domain.model.ApprovalStatus;
import com.example.agenticrag.infra.persistence.ComponentRegistryRepository;
import com.example.agenticrag.infra.persistence.KnowledgeApprovalRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Loop HITL: aprovar indexa como LLM_APPROVED; rejeitar não indexa. */
class KnowledgeApprovalIT extends AbstractIntegrationTest {

    @Autowired
    KnowledgeApprovalService service;
    @Autowired
    KnowledgeApprovalRepository approvals;
    @Autowired
    ComponentRegistryRepository registry;
    @Autowired
    VectorStore vectorStore;

    @Test
    void aprovarIndexaConhecimento() {
        registry.upsert("payments-sdk", "Payments SDK", "2.3.1", "desc", "hash");
        UUID id = approvals.savePending("payments-sdk",
                "Como estornar?", "Use POST /v2/charges/{id}/refund com o valor.");

        ApprovalRecord approved = service.approve(id, "fernando", "revisado");

        assertThat(approved.status()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approved.vectorDocId()).isNotBlank();
        assertThat(approved.reviewer()).isEqualTo("fernando");

        assertThat(vectorStore.similaritySearch(SearchRequest.builder()
                .query("estorno de cobrança")
                .topK(5)
                .filterExpression("component_id == 'payments-sdk'")
                .build()))
                .anyMatch(d -> "LLM_APPROVED".equals(d.getMetadata().get("source")));
    }

    @Test
    void rejeitarNaoIndexa() {
        registry.upsert("payments-sdk", "Payments SDK", "2.3.1", "desc", "hash");
        UUID id = approvals.savePending("payments-sdk", "pergunta", "resposta");

        ApprovalRecord rejected = service.reject(id, "fernando", "impreciso");

        assertThat(rejected.status()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(vectorStore.similaritySearch(SearchRequest.builder()
                .query("resposta")
                .topK(5)
                .filterExpression("component_id == 'payments-sdk'")
                .build()))
                .noneMatch(d -> "LLM_APPROVED".equals(d.getMetadata().get("source")));
    }
}
