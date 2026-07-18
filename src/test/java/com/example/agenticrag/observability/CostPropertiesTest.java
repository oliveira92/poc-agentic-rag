package com.example.agenticrag.observability;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** A04 — custo por família de modelo: é o que faz o roteamento por risco virar economia medível. */
class CostPropertiesTest {

    private static CostProperties props() {
        return new CostProperties("USD", 0.0, 0.0, Map.of(
                "haiku", new CostProperties.Price(0.001, 0.005),
                "sonnet", new CostProperties.Price(0.003, 0.015)));
    }

    @Test
    void matchesFamilyBySubstringOfExactId() {
        // ids reais da conta
        assertThat(props().priceFor("claude-haiku-4-5-20251001").input()).isEqualTo(0.001);
        assertThat(props().priceFor("claude-sonnet-5").output()).isEqualTo(0.015);
    }

    @Test
    void haikuIsAFractionOfSonnetForSameTokens() {
        CostProperties p = props();
        double haiku = p.estimate(700, 800, "claude-haiku-4-5-20251001");
        double sonnet = p.estimate(700, 800, "claude-sonnet-5");
        assertThat(haiku).isLessThan(sonnet);
        assertThat(sonnet / haiku).isGreaterThan(2.5); // rota forte custa bem mais por token
    }

    @Test
    void fallsBackToGlobalWhenNoFamilyMatches() {
        CostProperties p = new CostProperties("USD", 0.002, 0.006, Map.of());
        assertThat(p.estimate(1000, 1000, "gpt-4o")).isEqualTo(0.002 + 0.006, within(1e-9));
    }
}
