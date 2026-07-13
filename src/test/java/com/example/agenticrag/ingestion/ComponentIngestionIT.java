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

        // Escopo: o filtro por componente só devolve documentos do payments-sdk.
        List<Document> scoped = vectorStore.similaritySearch(SearchRequest.builder()
                .query("componente de pagamentos")
                .topK(20)
                .filterExpression("component_id == 'payments-sdk'")
                .build());
        assertThat(scoped).isNotEmpty();
        assertThat(scoped).allMatch(d -> "payments-sdk".equals(d.getMetadata().get("component_id")));

        // Ranking em PT-BR (regressão do modelo multilíngue): a consulta "estornar"
        // recupera o endpoint de refund no TOP-3. Isto reprovava com o MiniLM inglês.
        List<Document> pt = vectorStore.similaritySearch(SearchRequest.builder()
                .query("como faço para estornar uma cobrança")
                .topK(3)
                .filterExpression("component_id == 'payments-sdk'")
                .build());
        assertThat(pt).anyMatch(d -> String.valueOf(d.getMetadata().get("endpoint")).contains("refund"));
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
