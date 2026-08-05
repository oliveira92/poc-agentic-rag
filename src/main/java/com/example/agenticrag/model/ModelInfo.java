package com.example.agenticrag.model;

/**
 * Um modelo de chat selecionável pelo cliente.
 *
 * @param id          id exato do modelo (o que vai para o provedor). Com o gateway LiteLLM é o
 *                    <b>alias público</b> do proxy (ex.: {@code claude-sonnet-5}, {@code gpt-4o})
 * @param label       nome amigável para UI
 * @param tier        classe de uso: {@code forte} | {@code rapido} | {@code default} (alinha ao roteamento por risco)
 * @param description para que serve / quando usar
 * @param source      de onde veio: {@code catalog} (allow-list versionada) | {@code litellm} | {@code anthropic}
 * @param provider    vendor por trás do alias ({@code anthropic}, {@code openai}, {@code gemini}...),
 *                    quando o gateway informa — é o que mostra, na UI, que a PoC deixou de ser mono-provedor
 */
public record ModelInfo(
        String id,
        String label,
        String tier,
        String description,
        String source,
        String provider) {
}
