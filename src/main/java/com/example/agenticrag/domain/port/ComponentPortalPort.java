package com.example.agenticrag.domain.port;

import com.example.agenticrag.domain.model.ComponentDetails;

import java.util.Optional;

/**
 * Porta (hexagonal) para o portal de componentes — nossa Anti-Corruption Layer.
 *
 * <p>O domínio depende desta interface, nunca do cliente HTTP concreto. Isso permite:
 * trocar o portal, mockar em testes, e (Fase 4) chamar a API ao vivo como uma "tool".
 */
public interface ComponentPortalPort {

    /** Busca os metadados estruturados do componente (endpoints, versão, etc.). */
    Optional<ComponentDetails> fetchComponent(String componentId);

    /** Busca o README (markdown) do componente, quando disponível no portal. */
    Optional<String> fetchReadme(String componentId);
}
