package com.example.agenticrag.model;

/**
 * Modelo solicitado não está na allow-list (nem na lista ao vivo da conta). Mapeada para
 * HTTP 400 (validação) — diferente de {@code IllegalArgumentException} do domínio, que é 404.
 */
public class UnknownModelException extends RuntimeException {

    public UnknownModelException(String modelId) {
        super("Modelo não disponível: '" + modelId
                + "'. Consulte GET /api/v1/models para os modelos aceitos.");
    }
}
