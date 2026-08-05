package com.example.agenticrag.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;

/**
 * Base dos clientes de descoberta: cache com TTL + falha não-fatal.
 *
 * <p>A listagem é gratuita, mas bater no backend a cada request seria latência e ponto de falha
 * desnecessários — e a validação do {@code /advise} precisa ser síncrona e barata. Daí o cache:
 * {@link #list()} respeita o TTL e, em qualquer erro, devolve o que já tinha (fail-open na
 * DESCOBERTA; a allow-list do catálogo é quem realmente autoriza).
 */
abstract class CachingModelDiscoveryClient implements ModelDiscoveryClient {

    private static final Logger log = LoggerFactory.getLogger(CachingModelDiscoveryClient.class);

    private final ModelsProperties props;

    private volatile List<ModelInfo> cache = List.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    protected CachingModelDiscoveryClient(ModelsProperties props) {
        this.props = props;
    }

    /** Busca real no backend. Pode lançar — o caller trata. */
    protected abstract List<ModelInfo> fetch() throws Exception;

    @Override
    public final synchronized List<ModelInfo> list() {
        if (!enabled()) {
            return List.of();
        }
        if (!cache.isEmpty() && Instant.now().isBefore(fetchedAt.plus(props.cacheTtl()))) {
            return cache;
        }
        try {
            cache = List.copyOf(fetch());
            fetchedAt = Instant.now();
            log.debug("Descoberta ao vivo ({}): {} modelos.", provider(), cache.size());
        } catch (Exception e) {
            log.warn("Falha ao listar modelos em '{}' (usando cache/catálogo): {}", provider(), e.getMessage());
        }
        return cache;
    }

    @Override
    public final List<ModelInfo> cached() {
        return cache;
    }

    /** Trata placeholders de config ("not-set", vazio) como "sem credencial". */
    protected static boolean hasCredential(String key) {
        return key != null && !key.isBlank() && !key.equals("not-set");
    }
}
