package com.example.agenticrag.advisor;

import com.example.agenticrag.domain.model.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rerank por fonte (HU-08) — regressão real pega pelo quality gate: Q&As aprovados grandes
 * tomaram o top-6 e expulsaram fontes primárias (g07 caiu). Aprovados ficam limitados a
 * N vagas; as primárias seguintes na ordem de score ocupam o resto.
 */
class SelectWithPrecedenceTest {

    private static Document doc(String id, KnowledgeSource source) {
        return Document.builder().id(id).text("conteudo " + id)
                .metadata(Map.of(KnowledgeSource.METADATA_KEY, source.name(), "component_id", "c"))
                .build();
    }

    private static List<String> ids(List<Document> docs) {
        return docs.stream().map(Document::getId).toList();
    }

    @Test
    void approvedDocsAreCappedAndPrimariesBackfill() {
        // ranking por score: 3 aprovados na frente, primárias atrás (o cenário da regressão)
        List<Document> ranked = List.of(
                doc("qa1", KnowledgeSource.LLM_APPROVED),
                doc("qa2", KnowledgeSource.LLM_APPROVED),
                doc("qa3", KnowledgeSource.LLM_APPROVED),
                doc("api1", KnowledgeSource.PORTAL_API),
                doc("readme1", KnowledgeSource.README),
                doc("readme2", KnowledgeSource.README),
                doc("api2", KnowledgeSource.PORTAL_API));

        List<Document> top4 = ComponentAdvisorService.selectWithPrecedence(ranked, 4, 2);

        // qa3 (3º aprovado) é pulado; as primárias entram na ordem de score
        assertThat(ids(top4)).containsExactly("qa1", "qa2", "api1", "readme1");
    }

    @Test
    void orderByScoreIsPreservedWhenUnderTheCap() {
        List<Document> ranked = List.of(
                doc("api1", KnowledgeSource.PORTAL_API),
                doc("qa1", KnowledgeSource.LLM_APPROVED),
                doc("readme1", KnowledgeSource.README));
        assertThat(ids(ComponentAdvisorService.selectWithPrecedence(ranked, 3, 2)))
                .containsExactly("api1", "qa1", "readme1");
    }

    @Test
    void onlyApprovedAvailableStillFillsUpToCap() {
        List<Document> ranked = List.of(
                doc("qa1", KnowledgeSource.LLM_APPROVED),
                doc("qa2", KnowledgeSource.LLM_APPROVED),
                doc("qa3", KnowledgeSource.LLM_APPROVED));
        assertThat(ids(ComponentAdvisorService.selectWithPrecedence(ranked, 3, 2)))
                .containsExactly("qa1", "qa2");   // melhor 2 aprovados do que top-3 só de QA
    }
}
