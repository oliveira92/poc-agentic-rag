package com.example.agenticrag.domain.model;

import java.util.List;

/**
 * Representação canônica de um componente do portal (modelo de domínio).
 *
 * <p>Este é o contrato interno estável: mudou o portal, muda só o adapter, não o domínio.
 *
 * @param id            identificador do componente no portal
 * @param name          nome de exibição
 * @param version       versão corrente
 * @param description   descrição funcional
 * @param tags          rótulos (ex.: "pagamentos", "grpc")
 * @param repositoryUrl repositório de código, se houver
 * @param endpoints     endpoints expostos
 */
public record ComponentDetails(
        String id,
        String name,
        String version,
        String description,
        List<String> tags,
        String repositoryUrl,
        List<ComponentEndpoint> endpoints) {

    public ComponentDetails {
        tags = tags == null ? List.of() : List.copyOf(tags);
        endpoints = endpoints == null ? List.of() : List.copyOf(endpoints);
    }
}
