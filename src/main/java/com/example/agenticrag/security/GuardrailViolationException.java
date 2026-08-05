package com.example.agenticrag.security;

import java.util.List;
import java.util.Map;

/**
 * Requisição barrada por um controle. Vira HTTP 422 no {@code ApiExceptionHandler}.
 *
 * <p>A mensagem é deliberadamente genérica e igual para todos os controles: detalhar qual
 * padrão casou entrega ao atacante um oráculo para iterar até passar. Auditoria e demo veem
 * os ids dos controles (que não revelam a regra); o motivo detalhado fica no log/trace.
 */
public class GuardrailViolationException extends RuntimeException {

    private static final String PUBLIC_MESSAGE =
            "Requisição bloqueada pela política de segurança do assistente.";

    private final transient GuardDecision decision;

    public GuardrailViolationException(GuardDecision decision) {
        super(PUBLIC_MESSAGE);
        this.decision = decision;
    }

    public GuardStage stage() {
        return decision.stage();
    }

    public List<String> controlIds() {
        return decision.controlIds();
    }

    /** Projeção segura dos achados para o corpo do 422 (sem o trecho que casou). */
    public List<Map<String, Object>> publicFindings() {
        return decision.findings().stream()
                .filter(f -> f.action() == GuardAction.BLOCK)
                .<Map<String, Object>>map(f -> Map.of(
                        "control", f.controlId(),
                        "category", f.category().name(),
                        "detail", f.detail()))
                .toList();
    }

    public GuardDecision decision() {
        return decision;
    }
}
