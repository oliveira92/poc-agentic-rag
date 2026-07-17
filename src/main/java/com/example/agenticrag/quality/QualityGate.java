package com.example.agenticrag.quality;

import com.example.agenticrag.routing.RiskTier;

import java.util.List;

/**
 * Regra de decisão do <b>quality gate</b> (A05 — "O Portão"). Lógica pura e testável: dadas
 * as avaliações dos casos do golden set, decide se o deploy passa. É o teste de regressão da
 * IA — só que a métrica é groundedness (A03), não "compilou".
 *
 * <p>Regra combinada: (1) TODA rota de risco alto (HIGH) precisa passar — errar em sinistro/
 * pagamento é risco legal; (2) a taxa geral de aprovação precisa ficar acima do limite. Se
 * qualquer uma falhar, o gate barra (bloqueia o merge no CI).
 */
public final class QualityGate {

    private QualityGate() {
    }

    /**
     * Avaliação de um caso do golden set.
     *
     * @param id           id do caso
     * @param risk         nível de risco
     * @param retrievalHit a fonte esperada apareceu no top-K (ou, em armadilha, nada foi afirmado)
     * @param groundedness nota do juiz para a resposta
     * @param threshold    limite (0..1) exigido para a rota (mais rígido em HIGH)
     * @param passed       o caso passou (retrievalHit && groundedness >= threshold)
     */
    public record CaseEvaluation(
            String id,
            RiskTier risk,
            boolean retrievalHit,
            GroundednessResult groundedness,
            double threshold,
            boolean passed) {
    }

    /**
     * Relatório agregado do gate.
     *
     * @param total          nº de casos
     * @param passed         nº de casos aprovados
     * @param hitRate        fração de casos com retrieval correto
     * @param avgGroundedness média das notas de groundedness (0..1 normalizado)
     * @param gatePassed     decisão final do portão
     * @param cases          detalhe por caso (evidência)
     */
    public record Report(
            int total,
            int passed,
            double hitRate,
            double avgGroundedness,
            boolean gatePassed,
            List<CaseEvaluation> cases) {
    }

    /**
     * @param cases       casos avaliados
     * @param minPassRate taxa mínima de aprovação geral (ex.: 0.80)
     */
    public static Report decide(List<CaseEvaluation> cases, double minPassRate) {
        int total = cases.size();
        if (total == 0) {
            return new Report(0, 0, 0, 0, false, cases);
        }
        int passed = 0;
        int hits = 0;
        double groundSum = 0;
        boolean allHighRiskPass = true;
        for (CaseEvaluation c : cases) {
            if (c.passed()) {
                passed++;
            }
            if (c.retrievalHit()) {
                hits++;
            }
            if (c.groundedness() != null) {
                groundSum += c.groundedness().normalized();
            }
            if (c.risk() == RiskTier.HIGH && !c.passed()) {
                allHighRiskPass = false;
            }
        }
        double passRate = (double) passed / total;
        boolean gatePassed = allHighRiskPass && passRate >= minPassRate;
        return new Report(total, passed, (double) hits / total, groundSum / total, gatePassed, cases);
    }
}
