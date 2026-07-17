package com.example.agenticrag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Descobre os modelos <b>realmente disponíveis na conta</b> via {@code GET /v1/models} da
 * Anthropic — a fonte da verdade dos ids exatos (o material insiste: "use um id EXATO da conta").
 * Listar modelos é gratuito.
 *
 * <p>Resiliente e opcional: sem chave (perfil mock/CI) devolve vazio; qualquer falha de rede é
 * não-fatal (loga e cai no que tiver em cache). A lista fica em cache com TTL para não bater na
 * API a cada request — a validação de {@code /advise} usa o cache, nunca força rede.
 */
@Component
public class AnthropicModelsClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicModelsClient.class);
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient client;
    private final String apiKey;
    private final ModelsProperties props;

    private volatile List<ModelInfo> cache = List.of();
    private volatile Instant fetchedAt = Instant.EPOCH;

    public AnthropicModelsClient(ModelsProperties props,
                                 @Value("${spring.ai.anthropic.api-key:}") String apiKey) {
        this.props = props;
        this.apiKey = apiKey;
        this.client = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    /** Só tenta a descoberta ao vivo com uma chave real configurada. */
    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("not-set");
    }

    /** Lista ao vivo (com cache/TTL). Nunca lança: em falha, devolve o último cache (ou vazio). */
    public synchronized List<ModelInfo> list() {
        if (!enabled()) {
            return List.of();
        }
        if (!cache.isEmpty() && Instant.now().isBefore(fetchedAt.plus(props.cacheTtl()))) {
            return cache;
        }
        try {
            AnthropicModelsResponse resp = client.get()
                    .uri("/v1/models?limit=100")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .retrieve()
                    .body(AnthropicModelsResponse.class);
            List<ModelInfo> out = new ArrayList<>();
            if (resp != null && resp.data() != null) {
                for (AnthropicModel m : resp.data()) {
                    String label = m.displayName() != null ? m.displayName() : m.id();
                    out.add(new ModelInfo(m.id(), label, null, null, "anthropic"));
                }
            }
            cache = List.copyOf(out);
            fetchedAt = Instant.now();
            log.debug("Descoberta ao vivo: {} modelos da Anthropic.", cache.size());
        } catch (Exception e) {
            log.warn("Falha ao listar modelos da Anthropic (usando cache/catálogo): {}", e.getMessage());
        }
        return cache;
    }

    /** Cache atual sem forçar rede — usado na validação rápida do /advise. */
    public List<ModelInfo> cached() {
        return cache;
    }

    // ---- DTOs da resposta de /v1/models ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicModelsResponse(List<AnthropicModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicModel(String id, @JsonProperty("display_name") String displayName, String type) {
    }
}
