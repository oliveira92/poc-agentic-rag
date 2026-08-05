package com.example.agenticrag.model;

import java.util.List;

/**
 * Descoberta ao vivo dos modelos <b>realmente disponíveis</b> para a aplicação.
 *
 * <p>Abstrai de quem perguntamos: o gateway {@link LiteLlmModelsClient} (padrão — devolve os
 * modelos de TODOS os provedores configurados no proxy) ou a Anthropic direto
 * ({@link AnthropicModelsClient}, fallback sem gateway). Trocar de provedor não muda nada
 * acima desta interface.
 *
 * <p>Contrato: nunca lança. Sem credencial ou com o backend fora do ar, devolve o último
 * cache (ou vazio) — a allow-list do catálogo continua valendo, então o app segue operando.
 */
public interface ModelDiscoveryClient {

    /** Nome do backend consultado ({@code litellm} | {@code anthropic}) — vira o {@code source} do {@link ModelInfo}. */
    String provider();

    /** {@code true} quando há credencial/endpoint para tentar a descoberta ao vivo. */
    boolean enabled();

    /** Lista ao vivo (com cache/TTL). Nunca lança: em falha, devolve o último cache. */
    List<ModelInfo> list();

    /** Cache atual, sem forçar rede — usado na validação rápida do {@code /advise}. */
    List<ModelInfo> cached();
}
