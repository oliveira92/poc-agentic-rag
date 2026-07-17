package com.example.agenticrag.model;

/**
 * Um modelo de chat selecionável pelo cliente.
 *
 * @param id          id exato do modelo (o que vai para o provedor — precisa ser exato p/ a conta)
 * @param label       nome amigável para UI
 * @param tier        classe de uso: {@code forte} | {@code rapido} | {@code default} (alinha ao roteamento por risco)
 * @param description para que serve / quando usar
 * @param source      {@code catalog} (allow-list versionada) | {@code anthropic} (descoberto ao vivo em /v1/models)
 */
public record ModelInfo(
        String id,
        String label,
        String tier,
        String description,
        String source) {
}
