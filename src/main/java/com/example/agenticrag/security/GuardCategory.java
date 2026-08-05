package com.example.agenticrag.security;

/**
 * Categorias de achado. É por elas que a ação é configurada (`app.security.actions.<estágio>`)
 * e por elas que os relatórios agregam — o id do controle (SEC-xx) identifica o detector,
 * a categoria identifica o <b>risco</b>.
 */
public enum GuardCategory {

    /** Dado pessoal do próprio interlocutor: CPF, telefone, e-mail, cartão. */
    PII,

    /** Credencial: chave de API, token, senha, chave privada, URI com senha. */
    SECRET,

    /** Tentativa de sobrescrever instruções, extrair o prompt de sistema ou trocar de papel. */
    PROMPT_INJECTION,

    /** URL na entrada — vetor de injeção indireta e de exfiltração. */
    URL,

    /** Payload binário/documento (PDF, PNG, ZIP, executável) onde só se espera texto. */
    BINARY,

    /** Pedido de dado pessoal de TERCEIRO (não do interlocutor). */
    THIRD_PARTY_PII,

    /** Recurso citado na pergunta não pertence a quem está perguntando (autorização, não texto). */
    RESOURCE_OWNERSHIP,

    /** Assunto fora do conteúdo que foi ingerido para o RAG. */
    OUT_OF_SCOPE,

    /** Texto acima do limite aceito — prompt stuffing / abuso de recurso. */
    OVERSIZE,

    /** A resposta está repetindo o prompt de sistema (extração bem-sucedida). */
    SYSTEM_PROMPT_LEAK
}
