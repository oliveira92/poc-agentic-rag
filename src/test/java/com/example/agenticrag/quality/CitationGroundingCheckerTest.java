package com.example.agenticrag.quality;

import com.example.agenticrag.advisor.Citation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A03 / HU-02 — guardrail de fidelidade determinístico (anti-alucinação). */
class CitationGroundingCheckerTest {

    private final CitationGroundingChecker checker = new CitationGroundingChecker();

    @Test
    void endpointPresentInCitationsIsGrounded() {
        List<Citation> cits = List.of(
                new Citation(1, "PORTAL_API", "POST /v2/charges", 0.5, "Cria cobrança"),
                new Citation(2, "PORTAL_API", "POST /v2/charges/{id}/refund", 0.4, "Estorna"));
        String answer = "Para estornar use POST /v2/charges/{id}/refund conforme [2].";
        CitationGroundingChecker.Result r = checker.check(answer, cits);
        assertThat(r.lowConfidence()).isFalse();
        assertThat(r.unsupportedEndpoints()).isEmpty();
    }

    @Test
    void fabricatedEndpointIsFlagged() {
        List<Citation> cits = List.of(
                new Citation(1, "PORTAL_API", "POST /v2/charges", 0.5, "Cria cobrança"));
        String answer = "Cancele a assinatura com DELETE /v2/subscriptions/{id}.";
        CitationGroundingChecker.Result r = checker.check(answer, cits);
        assertThat(r.lowConfidence()).isTrue();
        assertThat(r.unsupportedEndpoints()).containsExactly("DELETE /v2/subscriptions/{id}");
    }

    @Test
    void noEndpointsMeansNoFlag() {
        CitationGroundingChecker.Result r = checker.check("Autentique com Bearer token.", List.of());
        assertThat(r.lowConfidence()).isFalse();
    }

    @Test
    void concreteIdExampleMatchesPlaceholderEndpoint() {
        // caso real observado no /advise: o exemplo usa um id concreto no lugar de {id}
        List<Citation> cits = List.of(
                new Citation(1, "PORTAL_API", "POST /v2/charges/{id}/refund", 0.5, "Estorna total/parcial"));
        String answer = "Faça POST /v2/charges/chg_abc/refund para o estorno parcial [1].";
        CitationGroundingChecker.Result r = checker.check(answer, cits);
        assertThat(r.lowConfidence()).isFalse();
        assertThat(r.unsupportedEndpoints()).isEmpty();
    }

    @Test
    void wrongMethodOnSamePathIsFlagged() {
        List<Citation> cits = List.of(
                new Citation(1, "PORTAL_API", "POST /v2/charges/{id}/refund", 0.5, "Estorna"));
        // DELETE não é suportado, mesmo o path casando
        CitationGroundingChecker.Result r = checker.check("Use DELETE /v2/charges/xyz/refund.", cits);
        assertThat(r.lowConfidence()).isTrue();
    }
}
