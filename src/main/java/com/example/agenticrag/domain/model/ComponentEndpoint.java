package com.example.agenticrag.domain.model;

/**
 * Um endpoint exposto por um componente, no formato canônico do NOSSO domínio.
 * O adapter do portal traduz o payload externo para este record (Anti-Corruption Layer).
 *
 * @param method         verbo HTTP (GET, POST, ...)
 * @param path           caminho relativo do endpoint
 * @param summary        descrição curta do que faz
 * @param authRequired   se exige autenticação
 * @param requestSchema  esquema/exemplo do request (texto livre ou JSON)
 * @param responseSchema esquema/exemplo do response (texto livre ou JSON)
 */
public record ComponentEndpoint(
        String method,
        String path,
        String summary,
        boolean authRequired,
        String requestSchema,
        String responseSchema) {
}
