package com.example.agenticrag.advisor;

import com.example.agenticrag.domain.model.KnowledgeSource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Rerank léxico por título (precursor da busca híbrida da Fase 2) — cenário real do g07:
 * o denso dava 0.299 para "README > Erros" na pergunta "Como os erros desta API são
 * formatados?", atrás de fontes genéricas; o casamento literal do termo no título corrige.
 */
class TitleMatchRerankTest {

    private static Document doc(String id, String section, double score) {
        return Document.builder().id(id).text("conteudo")
                .metadata(Map.of(
                        KnowledgeSource.METADATA_KEY, KnowledgeSource.README.name(),
                        "component_id", "c",
                        "section", section))
                .score(score)
                .build();
    }

    private static List<String> ids(List<Document> docs) {
        return docs.stream().map(Document::getId).toList();
    }

    @Test
    void termInTitleBoostsSectionAboveGenericSources() {
        // ranking denso real do g07: Erros em último, atrás de overview/autenticação
        List<Document> ranked = List.of(
                doc("auth", "Autenticação", 0.309),
                doc("intro", "Introdução", 0.305),
                doc("erros", "Erros", 0.299));

        List<Document> reranked = ComponentAdvisorService.rerankByTitleMatch(
                ranked, "Como os erros desta API são formatados?");

        assertThat(ids(reranked)).first().isEqualTo("erros"); // 0.299+0.12 supera os genéricos
    }

    @Test
    void accentsAreNormalizedBothWays() {
        List<Document> ranked = List.of(
                doc("rate", "Rate limiting", 0.40),
                doc("idem", "Idempotência", 0.30));
        // "idempotencia" sem acento na pergunta casa com o título acentuado
        List<Document> reranked = ComponentAdvisorService.rerankByTitleMatch(
                ranked, "como garantir idempotencia?");
        assertThat(ids(reranked)).first().isEqualTo("idem");
    }

    @Test
    void noTitleMatchPreservesDenseOrder() {
        List<Document> ranked = List.of(
                doc("a", "Autenticação", 0.5),
                doc("b", "Erros", 0.4));
        List<Document> reranked = ComponentAdvisorService.rerankByTitleMatch(
                ranked, "o sdk envia notificações por WhatsApp?"); // pergunta-armadilha
        assertThat(ids(reranked)).containsExactly("a", "b");
    }
}
