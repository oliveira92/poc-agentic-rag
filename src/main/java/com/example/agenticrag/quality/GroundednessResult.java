package com.example.agenticrag.quality;

/**
 * Resultado de uma avaliação de <b>groundedness</b> (fundamentação) — o SLI de qualidade
 * nº 1 do RAG corporativo (A03). Cada afirmação da resposta está apoiada no contexto
 * recuperado? Groundedness baixo = alucinação.
 *
 * @param score  nota de 1 (inventou) a 5 (totalmente ancorado)
 * @param reason justificativa do juiz (o "ouro para diagnosticar" — leia sempre)
 * @param judge  quem avaliou ("mock" | "llm:<modelo>") — rastreabilidade
 */
public record GroundednessResult(int score, String reason, String judge) {

    /** Nota normalizada 0..1 para comparar com o SLO de qualidade por rota. */
    public double normalized() {
        return Math.max(0, Math.min(5, score)) / 5.0;
    }

    public boolean meets(double thresholdNormalized) {
        return normalized() >= thresholdNormalized;
    }
}
