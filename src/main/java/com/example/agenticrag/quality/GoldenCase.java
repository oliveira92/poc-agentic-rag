package com.example.agenticrag.quality;

import com.example.agenticrag.routing.RiskTier;

/**
 * Um caso do <b>golden set</b> (A03/A05): pergunta com a resposta/fonte esperada conhecida.
 * O golden set é o "combustível" da avaliação — sem ele o juiz não tem contra o quê comparar.
 * Boa composição: ~40% fáceis, ~40% médios, ~20% armadilhas (as que revelam alucinação).
 *
 * @param id          identificador estável do caso
 * @param componentId componente alvo (ex.: payments-sdk)
 * @param question    a pergunta do usuário
 * @param expectedRef fonte/endpoint que DEVE ser recuperado (ex.: "POST /v2/charges/{id}/refund"),
 *                    ou vazio para armadilhas (fora de escopo — nada deve ser afirmado)
 * @param risk        nível de risco esperado (dirige o limite do gate)
 * @param kind        "facil" | "medio" | "armadilha" (documentação da cobertura)
 */
public record GoldenCase(
        String id,
        String componentId,
        String question,
        String expectedRef,
        RiskTier risk,
        String kind) {

    public boolean isTrap() {
        return expectedRef == null || expectedRef.isBlank();
    }
}
