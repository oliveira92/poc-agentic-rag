package com.example.agenticrag.domain.model;

/**
 * Origem de um chunk na base de conhecimento vetorial.
 *
 * <p>A distinção é deliberada: fontes primárias ({@link #PORTAL_API}, {@link #README})
 * têm precedência sobre {@link #LLM_APPROVED} para mitigar realimentação/eco do modelo.
 * O metadado {@code source} é gravado em cada {@code Document} e usado em filtros e rerank.
 */
public enum KnowledgeSource {
    PORTAL_API,
    README,
    LLM_APPROVED;

    public static final String METADATA_KEY = "source";
}
