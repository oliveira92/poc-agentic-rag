package com.example.agenticrag.security;

import java.util.List;

/**
 * Veredito de um estágio de guarda: o que fazer, o texto já sanitizado e por quê.
 *
 * @param stage    estágio que produziu o veredito
 * @param action   ação resultante (a mais severa entre os achados)
 * @param text     texto a usar daqui em diante — mascarado se {@link GuardAction#MASK},
 *                 o original se {@link GuardAction#ALLOW}, e o original (não usado) se BLOCK
 * @param findings achados que sustentam a decisão (vazio quando ALLOW)
 */
public record GuardDecision(
        GuardStage stage,
        GuardAction action,
        String text,
        List<GuardFinding> findings) {

    public static GuardDecision allow(GuardStage stage, String text) {
        return new GuardDecision(stage, GuardAction.ALLOW, text, List.of());
    }

    public boolean blocked() {
        return action == GuardAction.BLOCK;
    }

    public boolean masked() {
        return action == GuardAction.MASK;
    }

    /** Ids dos controles que dispararam — o que vai para métrica, span e relatório. */
    public List<String> controlIds() {
        return findings.stream().map(GuardFinding::controlId).distinct().toList();
    }

    public List<GuardCategory> categories() {
        return findings.stream().map(GuardFinding::category).distinct().toList();
    }
}
