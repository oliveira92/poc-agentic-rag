package com.example.agenticrag.advisor;

import java.util.List;
import java.util.UUID;

/**
 * Resposta do agente sobre como consumir um componente.
 *
 * @param componentId          componente consultado
 * @param conversationId       id da conversa (memória de curto prazo)
 * @param answer               resposta gerada pela LLM, fundamentada nas citações
 * @param citations            fontes recuperadas do pgvector
 * @param grounded             true se houve base recuperada (senão a resposta é fraca/aviso)
 * @param approvalId           id do rascunho salvo para eventual aprovação humana (HITL)
 * @param traceId              id do trace (Langfuse/OTel) — correlaciona esta resposta a
 *                             logs, métricas e ao feedback do usuário (A01: "o trace_id amarra tudo")
 * @param route                rota de negócio + risco em que a pergunta caiu (A05)
 * @param model                modelo que realmente respondeu (roteamento por risco)
 * @param lowConfidence        guardrail de fidelidade: o texto citou endpoint fora das fontes (A03)
 * @param unsupportedEndpoints endpoints citados no texto que NÃO constam das citações (evidência)
 */
public record AdviceResult(
        String componentId,
        String conversationId,
        String answer,
        List<Citation> citations,
        boolean grounded,
        UUID approvalId,
        String traceId,
        String route,
        String model,
        boolean lowConfidence,
        List<String> unsupportedEndpoints) {
}
