package com.example.agenticrag.routing;

/**
 * Nível de risco de uma consulta — dirige a escolha de modelo e o SLO de qualidade (A03/A05).
 *
 * <p>A tese do módulo: "responder ancorado + rotear por risco". Rota de risco alto exige
 * o modelo forte e um limite de fundamentação mais rígido; rota trivial pode usar o modelo
 * rápido/barato (o roteamento correto economiza — gráfico "−44%" da A05).
 */
public enum RiskTier {
    /** FAQ / conceitual (ex.: "o que é o componente"). Baixo risco → modelo rápido. */
    LOW,
    /** Integração geral ("como consumo X"). Equilíbrio. */
    MEDIUM,
    /** Movimento de dinheiro, auth, idempotência, erros em produção → modelo forte + gate rígido. */
    HIGH
}
