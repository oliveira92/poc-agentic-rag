package com.example.agenticrag.advisor;

import java.util.List;
import java.util.UUID;

/**
 * Resposta do agente sobre como consumir um componente.
 *
 * @param componentId    componente consultado
 * @param conversationId id da conversa (memória de curto prazo)
 * @param answer         resposta gerada pela LLM, fundamentada nas citações
 * @param citations      fontes recuperadas do pgvector
 * @param grounded       true se houve base recuperada (senão a resposta é fraca/aviso)
 * @param approvalId     id do rascunho salvo para eventual aprovação humana (HITL)
 */
public record AdviceResult(
        String componentId,
        String conversationId,
        String answer,
        List<Citation> citations,
        boolean grounded,
        UUID approvalId) {
}
