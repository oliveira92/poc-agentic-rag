package com.example.agenticrag.advisor;

/**
 * Uma fonte usada para fundamentar a resposta (rastreabilidade / anti-alucinação).
 *
 * @param index   número da citação referenciado no texto ([1], [2], ...)
 * @param source  origem: PORTAL_API | README | LLM_APPROVED
 * @param ref     referência humana (ex.: "POST /v2/charges" ou seção do README)
 * @param score   similaridade (quando disponível)
 * @param snippet trecho recuperado
 */
public record Citation(
        int index,
        String source,
        String ref,
        Double score,
        String snippet) {
}
