package com.example.agenticrag.quality;

/**
 * Avaliador de fundamentação (A03). Duas implementações, escolhidas por
 * {@code app.quality.judge}:
 * <ul>
 *   <li>{@code mock} (default) — heurística determinística, roda no CI sem chave ("Sem chave? roda em mock");</li>
 *   <li>{@code llm} — LLM-as-judge: um modelo lê (pergunta, contexto, resposta) e devolve nota + justificativa.</li>
 * </ul>
 *
 * <p>Cuidado ensinado na aula: o juiz-LLM tem viés (premia verbosidade/fluência). Antes de
 * confiar cego no automático, calibre contra humano numa amostra. Por isso o resultado
 * sempre carrega {@code judge} e {@code reason} — para auditar a nota.
 */
public interface GroundednessEvaluator {

    /**
     * @param question pergunta original do usuário
     * @param context  contexto recuperado (as fontes numeradas)
     * @param answer   resposta gerada a ser avaliada
     */
    GroundednessResult evaluate(String question, String context, String answer);
}
