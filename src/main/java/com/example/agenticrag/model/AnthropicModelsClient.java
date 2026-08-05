package com.example.agenticrag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Descoberta direto na Anthropic ({@code GET /v1/models}) — caminho <b>sem gateway</b>, ativado com
 * {@code app.models.provider=anthropic}. Fica como fallback da Fase 1: um provedor só, sem infra
 * extra. O padrão hoje é o {@link LiteLlmModelsClient}.
 *
 * <p>Sem chave (perfil mock/CI) devolve vazio; falha de rede é não-fatal (ver a base).
 */
@Component
@ConditionalOnProperty(name = "app.models.provider", havingValue = "anthropic")
public class AnthropicModelsClient extends CachingModelDiscoveryClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient client;
    private final String apiKey;

    public AnthropicModelsClient(ModelsProperties props,
                                 @Value("${spring.ai.anthropic.api-key:}") String apiKey) {
        super(props);
        this.apiKey = apiKey;
        this.client = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "anthropic";
    }

    /** Listar modelos é gratuito, mas exige chave real — sem ela nem tentamos. */
    @Override
    public boolean enabled() {
        return hasCredential(apiKey);
    }

    @Override
    protected List<ModelInfo> fetch() {
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
                out.add(new ModelInfo(m.id(), label, null, null, provider(), "anthropic"));
            }
        }
        return out;
    }

    // ---- DTOs da resposta de /v1/models ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicModelsResponse(List<AnthropicModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AnthropicModel(String id, @JsonProperty("display_name") String displayName, String type) {
    }
}
