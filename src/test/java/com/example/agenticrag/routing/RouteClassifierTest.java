package com.example.agenticrag.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A05 — roteamento por risco: termos de risco → HIGH (modelo forte); conceitual curto → LOW. */
class RouteClassifierTest {

    private final RouteClassifier classifier = new RouteClassifier(new RoutingProperties());

    @Test
    void moneyMovementIsHighRisk() {
        Route r = classifier.classify("payments-sdk", "Como faço para estornar uma cobrança?");
        assertThat(r.risk()).isEqualTo(RiskTier.HIGH);
        assertThat(r.name()).isEqualTo("risco");
    }

    @Test
    void idempotencyAndAuthAreHighRisk() {
        assertThat(classifier.classify("c", "Como garantir idempotência ao criar a cobrança?").risk())
                .isEqualTo(RiskTier.HIGH);
        assertThat(classifier.classify("c", "Como faço a autenticação com token?").risk())
                .isEqualTo(RiskTier.HIGH);
    }

    @Test
    void conceptualShortQuestionIsLowRisk() {
        Route r = classifier.classify("payments-sdk", "O que é o componente?");
        assertThat(r.risk()).isEqualTo(RiskTier.LOW);
        assertThat(r.name()).isEqualTo("faq");
    }

    @Test
    void generalIntegrationIsMedium() {
        Route r = classifier.classify("payments-sdk", "Quais campos devo enviar no corpo da requisição de criação?");
        assertThat(r.risk()).isEqualTo(RiskTier.MEDIUM);
        assertThat(r.name()).isEqualTo("integracao");
    }

    @Test
    void modelDefaultsToConfiguredValue() {
        RoutingProperties props = new RoutingProperties();
        props.setStrongModel("claude-strong");
        props.setFastModel("claude-fast");
        RouteClassifier c = new RouteClassifier(props);
        assertThat(c.classify("c", "como estornar cobrança?").model()).isEqualTo("claude-strong");
        assertThat(c.classify("c", "o que é isso?").model()).isEqualTo("claude-fast");
    }
}
