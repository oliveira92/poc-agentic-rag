package com.example.agenticrag.routing;

/**
 * Decisão de roteamento para uma consulta: em qual rota de negócio ela cai, qual o
 * nível de risco, e qual modelo real atende (alias de negócio → modelo, como no gateway
 * LiteLLM da A05).
 *
 * @param name      rótulo de negócio da rota (ex.: "faq", "integracao", "pagamento")
 * @param risk      nível de risco (dirige SLO de qualidade e escolha de modelo)
 * @param model     id do modelo escolhido para atender a rota
 */
public record Route(String name, RiskTier risk, String model) {

    /** Rótulo da rota para usar como dimensão em métricas/spans (nunca nulo). */
    public String tag() {
        return name == null ? "unknown" : name;
    }
}
