package com.example.agenticrag.security.scope;

/**
 * Veredito do juiz de escopo.
 *
 * @param inScope    a pergunta é sobre o conteúdo que foi ingerido?
 * @param confidence 0..1 — quanto o juiz confia no próprio veredito
 * @param reason     frase curta e exibível explicando a decisão
 * @param judge      qual juiz respondeu ({@code lexical}, {@code llm}, {@code llm:error}...)
 */
public record ScopeVerdict(boolean inScope, double confidence, String reason, String judge) {

    public static ScopeVerdict in(String reason, String judge) {
        return new ScopeVerdict(true, 1.0, reason, judge);
    }

    public static ScopeVerdict out(String reason, String judge) {
        return new ScopeVerdict(false, 1.0, reason, judge);
    }

    /** Juiz sem base para opinar — trata como dentro do escopo, mas com confiança zero. */
    public static ScopeVerdict abstain(String reason, String judge) {
        return new ScopeVerdict(true, 0.0, reason, judge);
    }
}
