package com.example.agenticrag.ingestion;

import com.example.agenticrag.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Ingestão real: portal mock -> embeddings ONNX -> pgvector -> recuperação. */
class ComponentIngestionIT extends AbstractIntegrationTest {

    @Autowired
    ComponentIngestionService ingestion;
    @Autowired
    VectorStore vectorStore;

    @Test
    void ingereEhIdempotenteERecupera() {
        IngestionResult first = ingestion.ingestFromPortal("payments-sdk");

        assertThat(first.skipped()).isFalse();
        assertThat(first.endpointsIndexed()).isEqualTo(3);
        assertThat(first.readmeChunks()).isEqualTo(5);
        assertThat(first.totalDocuments()).isEqualTo(9);

        // Idempotência: reingestão do mesmo conteúdo não reprocessa.
        assertThat(ingestion.ingestFromPortal("payments-sdk").skipped()).isTrue();

        // Recuperação filtrada pelo componente. Este IT valida o PIPELINE (escopo +
        // indexação + recall), não a QUALIDADE de ranking do modelo de embeddings
        // (limitada em PT com o all-MiniLM-L6-v2; ver Fase 2 no README).
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query("como consumir cobranças e estornos deste componente")
                .topK(20)
                .filterExpression("component_id == 'payments-sdk'")
                .build());

        // escopo: só retornam documentos do componente filtrado
        assertThat(docs).isNotEmpty();
        assertThat(docs).allMatch(d -> "payments-sdk".equals(d.getMetadata().get("component_id")));
        // recall: o endpoint de estorno foi indexado e é recuperável
        assertThat(docs).anyMatch(d -> String.valueOf(d.getMetadata().get("endpoint")).contains("refund"));
    }

    @Test
    void componenteInexistenteFalha() {
        assertThat(
                org.junit.jupiter.api.Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> ingestion.ingestFromPortal("nao-existe")))
                .hasMessageContaining("não encontrado");
    }
}
