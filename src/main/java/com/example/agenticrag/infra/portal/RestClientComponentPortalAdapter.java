package com.example.agenticrag.infra.portal;

import com.example.agenticrag.domain.model.ComponentDetails;
import com.example.agenticrag.domain.model.ComponentEndpoint;
import com.example.agenticrag.domain.port.ComponentPortalPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter REAL do portal (perfil != mock).
 *
 * <p><b>ATENÇÃO:</b> o mapeamento abaixo é um ESQUELETO. Ajuste os caminhos e a tradução
 * do JSON externo -> {@link ComponentDetails} conforme o contrato real do seu portal
 * (idealmente a partir do OpenAPI). Todo o resto do sistema é indiferente a essa mudança.
 */
@Component
@Profile("!mock")
public class RestClientComponentPortalAdapter implements ComponentPortalPort {

    private static final Logger log = LoggerFactory.getLogger(RestClientComponentPortalAdapter.class);

    private final RestClient client;

    public RestClientComponentPortalAdapter(
            @Value("${component.portal.base-url}") String baseUrl,
            @Value("${component.portal.token:}") String token) {
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeaders(h -> {
                    if (token != null && !token.isBlank()) {
                        h.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .build();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ComponentDetails> fetchComponent(String componentId) {
        try {
            Map<String, Object> body = client.get()
                    .uri("/components/{id}", componentId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> { /* trata como vazio */ })
                    .body(Map.class);
            return Optional.ofNullable(body).map(this::toDetails);
        } catch (Exception e) {
            log.warn("Falha ao buscar componente {} no portal: {}", componentId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<String> fetchReadme(String componentId) {
        try {
            String readme = client.get()
                    .uri("/components/{id}/readme", componentId)
                    .retrieve()
                    .body(String.class);
            return Optional.ofNullable(readme).filter(s -> !s.isBlank());
        } catch (Exception e) {
            log.warn("Falha ao buscar README de {}: {}", componentId, e.getMessage());
            return Optional.empty();
        }
    }

    /** TODO: adaptar ao schema real do portal. */
    @SuppressWarnings("unchecked")
    private ComponentDetails toDetails(Map<String, Object> body) {
        List<ComponentEndpoint> endpoints = ((List<Map<String, Object>>)
                body.getOrDefault("endpoints", List.of())).stream()
                .map(e -> new ComponentEndpoint(
                        str(e.get("method")),
                        str(e.get("path")),
                        str(e.get("summary")),
                        Boolean.TRUE.equals(e.get("authRequired")),
                        str(e.get("requestSchema")),
                        str(e.get("responseSchema"))))
                .toList();
        return new ComponentDetails(
                str(body.get("id")),
                str(body.get("name")),
                str(body.get("version")),
                str(body.get("description")),
                (List<String>) body.getOrDefault("tags", List.of()),
                str(body.get("repositoryUrl")),
                endpoints);
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }
}
