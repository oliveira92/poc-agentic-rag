package com.example.agenticrag.quality;

import com.example.agenticrag.routing.RiskTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A05 — regra de decisão do "Portão": barra a regressão, com rigor extra na rota de risco. */
class QualityGateTest {

    private static QualityGate.CaseEvaluation eval(String id, RiskTier risk, boolean passed) {
        return new QualityGate.CaseEvaluation(id, risk, passed, null, 0.8, passed);
    }

    @Test
    void allPassingOpensTheGate() {
        var report = QualityGate.decide(List.of(
                eval("a", RiskTier.HIGH, true),
                eval("b", RiskTier.MEDIUM, true),
                eval("c", RiskTier.LOW, true)), 0.75);
        assertThat(report.gatePassed()).isTrue();
        assertThat(report.passed()).isEqualTo(3);
        assertThat(report.hitRate()).isEqualTo(1.0);
    }

    @Test
    void oneHighRiskFailureBlocksTheGate() {
        var report = QualityGate.decide(List.of(
                eval("a", RiskTier.HIGH, false),   // rota de risco reprovou
                eval("b", RiskTier.MEDIUM, true),
                eval("c", RiskTier.LOW, true),
                eval("d", RiskTier.LOW, true)), 0.5);
        assertThat(report.gatePassed()).isFalse(); // mesmo com passRate 0.75 > 0.5
    }

    @Test
    void lowOverallPassRateBlocksTheGate() {
        var report = QualityGate.decide(List.of(
                eval("a", RiskTier.MEDIUM, true),
                eval("b", RiskTier.MEDIUM, false),
                eval("c", RiskTier.LOW, false),
                eval("d", RiskTier.LOW, false)), 0.75);
        assertThat(report.gatePassed()).isFalse();
    }

    @Test
    void trapMissesToleratedWhenRealCasesPass() {
        // 3 reais (todos passam) + 1 armadilha LOW que reprovou (over-retrieval) → ainda passa
        var report = QualityGate.decide(List.of(
                eval("g01", RiskTier.HIGH, true),
                eval("g02", RiskTier.MEDIUM, true),
                eval("g03", RiskTier.HIGH, true),
                eval("trap", RiskTier.LOW, false)), 0.75);
        assertThat(report.gatePassed()).isTrue();
    }
}
