package com.example.agenticrag.security.dataset;

import com.example.agenticrag.security.GuardAction;

import java.util.List;

/**
 * Resultado da execução do dataset — <b>linha de base × protegida</b>, com denominador em toda
 * taxa.
 *
 * <p>Percentual sem denominador esconde o tamanho da amostra ("bloqueamos 100% dos ataques" com
 * n=2 não é evidência de nada), e taxa agregada esconde o custo: um sistema que barra tudo tem
 * recall perfeito e é inútil. Por isso o relatório separa os dois denominadores — casos ruins e
 * casos legítimos — e nunca os soma.
 *
 * @param scenarioId id do cenário ({@code componentes}, {@code a05-seguros})
 * @param scenario  nome exibível do cenário executado
 * @param judge     juiz de escopo ativo na execução
 * @param baseline  métricas com os controles desligados
 * @param protectedRun métricas com os controles ligados
 * @param cases     resultado caso a caso (a evidência auditável)
 */
public record SecurityEvaluationReport(
        String scenarioId,
        String scenario,
        String judge,
        Metrics baseline,
        Metrics protectedRun,
        List<CaseResult> cases) {

    /**
     * Resultado de um caso nas duas execuções.
     *
     * @param id             id do caso
     * @param type           legitimo | abuso | fora_escopo
     * @param question       texto avaliado
     * @param shouldBlock    o esperado
     * @param baselineAction o que a linha de base fez
     * @param action         o que a versão protegida fez
     * @param controls       controles que dispararam (SEC-xx)
     * @param categories     categorias de risco identificadas
     * @param outcome        TP | FP | TN | FN em relação ao esperado
     * @param note           explicação quando o caso merece uma (falso positivo, MASK, lacuna)
     */
    public record CaseResult(
            String id,
            String type,
            String question,
            boolean shouldBlock,
            GuardAction baselineAction,
            GuardAction action,
            List<String> controls,
            List<String> categories,
            String outcome,
            String note) {
    }

    /**
     * Métricas de uma execução. Cada taxa vem acompanhada do seu denominador.
     *
     * @param totalCases       total de casos (n)
     * @param badCases         denominador dos ataques
     * @param legitimateCases  denominador dos legítimos
     * @param badBlocked       ataques barrados (TP)
     * @param badAllowed       ataques que passaram (FN)
     * @param legitimateBlocked legítimos barrados (FP) — o custo do controle
     * @param legitimateAllowed legítimos preservados (TN)
     * @param legitimateMasked  legítimos atendidos COM ofuscação (subconjunto dos preservados)
     */
    public record Metrics(
            int totalCases,
            int badCases,
            int legitimateCases,
            int badBlocked,
            int badAllowed,
            int legitimateBlocked,
            int legitimateAllowed,
            int legitimateMasked) {

        /** Recall / TPR — dos ataques, quantos foram barrados. Denominador: {@link #badCases}. */
        public double blockRate() {
            return ratio(badBlocked, badCases);
        }

        /** FPR — dos legítimos, quantos foram barrados. Denominador: {@link #legitimateCases}. */
        public double falsePositiveRate() {
            return ratio(legitimateBlocked, legitimateCases);
        }

        /** Dos legítimos, quantos continuaram sendo atendidos. */
        public double preservationRate() {
            return ratio(legitimateAllowed, legitimateCases);
        }

        /** Dos bloqueios emitidos, quantos eram de fato ataque. */
        public double precision() {
            return ratio(badBlocked, badBlocked + legitimateBlocked);
        }

        public double accuracy() {
            return ratio(badBlocked + legitimateAllowed, totalCases);
        }

        /** Nenhum legítimo barrado e nenhum ataque solto — o único resultado aceitável. */
        public boolean clean() {
            return legitimateBlocked == 0 && badAllowed == 0;
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0 : (double) numerator / denominator;
        }
    }
}
