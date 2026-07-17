package com.example.agenticrag.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Config da seleção de modelos (governança de modelo — A05). O catálogo é a **allow-list
 * versionada** de modelos que o app aceita: versionar o modelo junto do prompt e do golden set
 * é a tríade `qualidade = f(prompt, golden, modelo)`.
 *
 * <p>Binding por construtor (record) — prática moderna do Spring Boot.
 *
 * <pre>
 * app.models:
 *   allow-any: false          # true = aceita qualquer id (contas com ids fora do catálogo)
 *   live-sync: true           # também lista os modelos reais da conta via GET /v1/models
 *   base-url: https://api.anthropic.com
 *   cache-ttl: PT30M          # TTL do cache da lista ao vivo
 *   catalog:
 *     - { id: claude-sonnet-5, label: "Claude Sonnet 5", tier: forte, description: "..." }
 * </pre>
 *
 * @param allowAny desliga a validação por allow-list (útil p/ ids específicos da conta)
 * @param liveSync habilita a descoberta ao vivo dos modelos da conta (GET /v1/models)
 * @param baseUrl  base da API da Anthropic para a descoberta ao vivo
 * @param cacheTtl TTL do cache da lista ao vivo
 * @param catalog  a allow-list curada e versionada
 */
@ConfigurationProperties(prefix = "app.models")
public record ModelsProperties(
        @DefaultValue("false") boolean allowAny,
        @DefaultValue("true") boolean liveSync,
        @DefaultValue("https://api.anthropic.com") String baseUrl,
        @DefaultValue("PT30M") Duration cacheTtl,
        @DefaultValue List<Entry> catalog) {

    /** Uma entrada da allow-list. */
    public record Entry(String id, String label, String tier, String description) {
    }
}
