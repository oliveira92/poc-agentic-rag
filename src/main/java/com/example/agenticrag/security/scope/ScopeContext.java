package com.example.agenticrag.security.scope;

import java.util.List;

/**
 * O que o juiz sabe sobre o conteúdo ingerido — a "moldura" contra a qual a pergunta é medida.
 *
 * <p>É esta descrição que atende ao requisito do M04 de um juiz "capaz de entender o conteúdo
 * que foi ingerido para o RAG": sem ela, o juiz avaliaria a pergunta contra uma noção genérica
 * de assunto e reprovaria qualquer domínio novo assim que a base mudasse.
 *
 * @param componentId componente-alvo da pergunta
 * @param summary     descrição curta do que a base contém (nome/descrição do componente ou do domínio)
 * @param knownRefs   títulos/refs recuperáveis (seções de README, endpoints) — o vocabulário real da base
 */
public record ScopeContext(String componentId, String summary, List<String> knownRefs) {

    public ScopeContext {
        knownRefs = knownRefs == null ? List.of() : List.copyOf(knownRefs);
    }

    public static ScopeContext of(String componentId, String summary) {
        return new ScopeContext(componentId, summary, List.of());
    }
}
