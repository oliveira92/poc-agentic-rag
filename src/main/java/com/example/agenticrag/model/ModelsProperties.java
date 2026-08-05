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
 *   provider: litellm         # litellm (gateway multi-provedor) | anthropic (direto, sem gateway)
 *   allow-any: false          # true = aceita qualquer id publicado no gateway
 *   live-sync: true           # também lista os modelos reais do backend
 *   base-url: http://localhost:4000
 *   cache-ttl: PT30M          # TTL do cache da lista ao vivo
 *   catalog:
 *     - { id: claude-sonnet-5, label: "Claude Sonnet 5", tier: forte, description: "..." }
 * </pre>
 *
 * @param provider qual {@link ModelDiscoveryClient} atende a descoberta ao vivo
 * @param allowAny desliga a validação por allow-list (útil quando o proxy publica muitos aliases)
 * @param liveSync habilita a descoberta ao vivo dos modelos do backend
 * @param baseUrl  base do backend de descoberta (o proxy LiteLLM, ou a API da Anthropic)
 * @param cacheTtl TTL do cache da lista ao vivo
 * @param catalog  a allow-list curada e versionada
 */
@ConfigurationProperties(prefix = "app.models")
public record ModelsProperties(
        @DefaultValue("litellm") String provider,
        @DefaultValue("false") boolean allowAny,
        @DefaultValue("true") boolean liveSync,
        @DefaultValue("http://localhost:4000") String baseUrl,
        @DefaultValue("PT30M") Duration cacheTtl,
        @DefaultValue List<Entry> catalog) {

    /** Uma entrada da allow-list. */
    public record Entry(String id, String label, String tier, String description, String provider) {
    }
}
