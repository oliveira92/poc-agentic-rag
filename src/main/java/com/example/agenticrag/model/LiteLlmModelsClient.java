package com.example.agenticrag.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Descoberta de modelos no <b>gateway LiteLLM</b> — o motivo de existir do gateway: uma única
 * lista com os modelos de TODOS os provedores publicados no proxy (Anthropic, OpenAI, Gemini,
 * Bedrock, Groq, Ollama...), sem um client por vendor.
 *
 * <p>Duas rotas, nesta ordem:
 * <ol>
 *   <li>{@code GET /model/info} (nativa do LiteLLM) — traz o alias público, o modelo real por trás
 *       ({@code litellm_params.model}, de onde sai o <b>provedor</b>) e o {@code mode}, que deixa
 *       filtrar só os de chat: um modelo de embedding na lista do {@code /advise} seria um 400
 *       garantido na hora de usar;</li>
 *   <li>{@code GET /v1/models} (OpenAI-compatível) — fallback quando {@code /model/info} não está
 *       liberado para a chave (é rota de admin em algumas configurações).</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(name = "app.models.provider", havingValue = "litellm", matchIfMissing = true)
public class LiteLlmModelsClient extends CachingModelDiscoveryClient {

    /** Modos do LiteLLM que fazem sentido no /advise (o resto é embedding, imagem, áudio...). */
    private static final List<String> CHAT_MODES = List.of("chat", "completion", "responses");

    private final RestClient client;
    private final String apiKey;

    public LiteLlmModelsClient(ModelsProperties props,
                               @Value("${spring.ai.openai.api-key:}") String apiKey) {
        super(props);
        this.apiKey = apiKey;
        this.client = RestClient.builder().baseUrl(props.baseUrl()).build();
    }

    @Override
    public String provider() {
        return "litellm";
    }

    /**
     * O proxy pode rodar sem autenticação em dev ({@code LITELLM_MASTER_KEY} vazio), então a
     * ausência de chave não desabilita a descoberta — diferente da Anthropic, onde a chave é
     * obrigatória. Se o proxy exigir chave e não houver, o erro é engolido em {@code list()}.
     */
    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    protected List<ModelInfo> fetch() {
        List<ModelInfo> rich = fromModelInfo();
        return rich.isEmpty() ? fromOpenAiModels() : rich;
    }

    /** Rota nativa: alias + provedor real + modo. */
    private List<ModelInfo> fromModelInfo() {
        try {
            ModelInfoResponse resp = get("/model/info", ModelInfoResponse.class);
            if (resp == null || resp.data() == null) {
                return List.of();
            }
            Map<String, ModelInfo> byId = new LinkedHashMap<>();
            for (ModelInfoEntry e : resp.data()) {
                String id = e.modelName();
                if (id == null || id.isBlank() || !isChat(e)) {
                    continue;
                }
                String provider = providerOf(e.litellmParams() == null ? null : e.litellmParams().model());
                byId.putIfAbsent(id, new ModelInfo(id, id, null,
                        provider == null ? null : "Publicado no LiteLLM via " + provider,
                        provider(), provider));
            }
            return List.copyOf(byId.values());
        } catch (Exception e) {
            // /model/info costuma exigir chave de admin — cair no /v1/models não é erro.
            return List.of();
        }
    }

    /** Fallback OpenAI-compatível: só os ids. */
    private List<ModelInfo> fromOpenAiModels() {
        OpenAiModelsResponse resp = get("/v1/models", OpenAiModelsResponse.class);
        List<ModelInfo> out = new ArrayList<>();
        if (resp != null && resp.data() != null) {
            for (OpenAiModel m : resp.data()) {
                if (m.id() != null && !m.id().isBlank()) {
                    out.add(new ModelInfo(m.id(), m.id(), null, null, provider(), providerOf(m.ownedBy())));
                }
            }
        }
        return out;
    }

    private <T> T get(String uri, Class<T> type) {
        RestClient.RequestHeadersSpec<?> spec = client.get().uri(uri);
        if (hasCredential(apiKey)) {
            spec = spec.header("Authorization", "Bearer " + apiKey);
        }
        return spec.retrieve().body(type);
    }

    /** {@code mode} ausente = proxy antigo/parcial: não descartamos o modelo por falta de metadado. */
    private static boolean isChat(ModelInfoEntry e) {
        String mode = e.modelInfo() == null ? null : e.modelInfo().mode();
        return mode == null || CHAT_MODES.contains(mode.toLowerCase(Locale.ROOT));
    }

    /** "anthropic/claude-sonnet-5" -> "anthropic"; sem prefixo, o próprio valor (ex.: "openai"). */
    private static String providerOf(String upstreamModel) {
        if (upstreamModel == null || upstreamModel.isBlank()) {
            return null;
        }
        int slash = upstreamModel.indexOf('/');
        return slash > 0 ? upstreamModel.substring(0, slash) : upstreamModel;
    }

    // ---- DTOs ----

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfoResponse(List<ModelInfoEntry> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfoEntry(@JsonProperty("model_name") String modelName,
                          @JsonProperty("litellm_params") LitellmParams litellmParams,
                          @JsonProperty("model_info") ModelInfoDetails modelInfo) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record LitellmParams(String model) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfoDetails(String mode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiModelsResponse(List<OpenAiModel> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiModel(String id, @JsonProperty("owned_by") String ownedBy) {
    }
}
