package com.example.agenticrag.security.scope;

/**
 * Decide se a pergunta cabe no conteúdo que foi ingerido (risco R2 do threat model).
 *
 * <p>Duas implementações, escolhidas por {@code app.security.scope-judge}:
 * <ul>
 *   <li>{@link LexicalScopeJudge} ({@code lexical}, padrão) — determinístico, sem rede, sem
 *       chave. Roda no CI, roda na demo offline e dá o mesmo resultado toda vez, que é o que
 *       um controle precisa dar para ser testável;</li>
 *   <li>{@link LlmScopeJudge} ({@code llm}) — generaliza para formulações que nenhuma lista
 *       previu, ao custo de latência, dinheiro e não-determinismo.</li>
 * </ul>
 *
 * <p>Não são alternativas de gosto: a lista cobre o conhecido barato, o juiz cobre o
 * desconhecido caro. Em produção o desenho é a lista sempre + o juiz na rota de risco.
 */
public interface ScopeJudge {

    /** Nome do juiz, propagado para métricas e para o relatório do dataset. */
    String name();

    ScopeVerdict judge(String question, ScopeContext context);
}
