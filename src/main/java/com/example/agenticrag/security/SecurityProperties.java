package com.example.agenticrag.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * Política de segurança em <b>configuração</b>, não espalhada em {@code if}s pelo código.
 *
 * <p>É o que torna a matriz de evidências verificável: o auditor lê um bloco de YAML e sabe o
 * que acontece com um CPF na pergunta, com um segredo na ingestão e com uma injeção detectada —
 * sem abrir o Java. E é o que permite endurecer a política em produção (MASK → BLOCK) sem
 * recompilar.
 *
 * <pre>
 * app.security:
 *   enabled: true
 *   fail-mode: closed          # erro interno de um controle vira BLOCK (closed) ou ALLOW (open)
 *   max-question-chars: 4000
 *   max-document-chars: 400000
 *   scope-judge: lexical       # lexical | llm
 *   actions:
 *     input:   { pii: MASK, prompt-injection: BLOCK, ... }
 *     output:  { pii: MASK, system-prompt-leak: BLOCK }
 *     ingestion: { pii: BLOCK, secret: BLOCK, ... }
 *   scope:
 *     domain-terms: [...]      # vocabulário do que foi ingerido
 *     off-task-patterns: [...] # tarefas que o agente não faz, mesmo no domínio
 * </pre>
 *
 * @param enabled          desliga TODA a camada (só para medir a linha de base — ver o dataset)
 * @param failMode         {@code closed} = falha de controle bloqueia; {@code open} = deixa passar
 * @param maxQuestionChars teto da pergunta
 * @param maxDocumentChars teto do documento ingerido
 * @param scopeJudge       qual {@code ScopeJudge} usar
 * @param urlAllowlist     domínios internos que o SEC-05 aceita na entrada (vazio = bloqueia toda URL)
 * @param actions          ação por estágio e categoria
 * @param scope            vocabulário e padrões do juiz léxico
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("closed") String failMode,
        @DefaultValue("4000") int maxQuestionChars,
        @DefaultValue("400000") int maxDocumentChars,
        @DefaultValue("lexical") String scopeJudge,
        @DefaultValue List<String> urlAllowlist,
        @DefaultValue Map<GuardStage, Map<GuardCategory, GuardAction>> actions,
        @DefaultValue Scope scope) {

    /**
     * @param domainTerms     termos que indicam "isto é sobre o que foi ingerido"
     * @param offTaskPatterns tarefas que o agente não executa (regex), mesmo citando o domínio
     */
    public record Scope(
            @DefaultValue List<String> domainTerms,
            @DefaultValue List<String> offTaskPatterns) {
    }

    /**
     * Ação configurada para (estágio, categoria); {@link GuardAction#ALLOW} quando nada foi
     * declarado. O default silencioso é ALLOW de propósito: uma categoria nova, recém-criada no
     * código e ainda ausente do YAML, não pode começar bloqueando produção sem ninguém decidir.
     */
    public GuardAction actionFor(GuardStage stage, GuardCategory category) {
        Map<GuardCategory, GuardAction> byCategory = actions == null ? null : actions.get(stage);
        if (byCategory == null) {
            return GuardAction.ALLOW;
        }
        return byCategory.getOrDefault(category, GuardAction.ALLOW);
    }

    /** {@code true} = erro interno de um controle vira BLOCK (postura fail-safe). */
    public boolean failClosed() {
        return !"open".equalsIgnoreCase(failMode);
    }
}
