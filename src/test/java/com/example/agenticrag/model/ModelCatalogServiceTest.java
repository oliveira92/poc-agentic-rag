package com.example.agenticrag.model;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Seleção de modelos (A05): allow-list valida a escolha manual; allow-any é a válvula de escape. */
class ModelCatalogServiceTest {

    private static ModelCatalogService service(boolean allowAny) {
        ModelsProperties props = new ModelsProperties(
                "anthropic", allowAny, false, "https://api.anthropic.com", Duration.ofMinutes(30),
                List.of(new ModelsProperties.Entry("claude-sonnet-5", "Claude Sonnet 5", "forte", "d", "anthropic"),
                        new ModelsProperties.Entry("claude-haiku-4-5", "Claude Haiku 4.5", "rapido", "d", "anthropic")));
        // chave "not-set" => descoberta ao vivo desligada => sem rede
        AnthropicModelsClient live = new AnthropicModelsClient(props, "not-set");
        return new ModelCatalogService(props, live);
    }

    @Test
    void blankRequestMeansUseRouting() {
        ModelCatalogService s = service(false);
        assertThat(s.resolve(null)).isNull();
        assertThat(s.resolve("")).isNull();
        assertThat(s.resolve("   ")).isNull();
    }

    @Test
    void catalogModelIsAccepted() {
        assertThat(service(false).resolve("claude-sonnet-5")).isEqualTo("claude-sonnet-5");
    }

    @Test
    void unknownModelIsRejected() {
        ModelCatalogService s = service(false);
        assertThatThrownBy(() -> s.resolve("gpt-4o"))
                .isInstanceOf(UnknownModelException.class)
                .hasMessageContaining("GET /api/v1/models");
    }

    @Test
    void allowAnyBypassesValidation() {
        assertThat(service(true).resolve("qualquer-id-da-conta")).isEqualTo("qualquer-id-da-conta");
    }

    @Test
    void catalogIsExposedWithSource() {
        List<ModelInfo> catalog = service(false).catalog();
        assertThat(catalog).hasSize(2);
        assertThat(catalog).allMatch(m -> m.source().equals("catalog"));
        assertThat(catalog).anyMatch(m -> m.id().equals("claude-haiku-4-5") && m.tier().equals("rapido"));
    }
}
