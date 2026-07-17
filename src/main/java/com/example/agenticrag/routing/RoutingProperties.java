package com.example.agenticrag.routing;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Config do roteamento por risco (A05 — "aliases de negócio → modelo real").
 *
 * <p>Por padrão os três aliases apontam para o MESMO modelo default do app, de modo que o
 * comportamento não muda até que se decida ativar o roteamento por custo (basta apontar
 * {@code fast-model} para um modelo mais barato). A classificação da rota e a tag de
 * observabilidade são emitidas sempre — o roteamento fica visível mesmo sem troca de modelo.
 *
 * <pre>
 * app.routing.strong-model = claude-sonnet-5      # rotas de risco (pagamento, auth, erros)
 * app.routing.fast-model   = claude-sonnet-5      # troque p/ claude-haiku-4-5 e economize
 * app.routing.threshold-high = 0.90               # SLO de groundedness da rota de risco
 * app.routing.threshold-default = 0.80
 * </pre>
 */
@ConfigurationProperties(prefix = "app.routing")
public class RoutingProperties {

    /** Modelo forte para rotas de risco (HIGH). */
    private String strongModel = "claude-sonnet-5";

    /** Modelo rápido/barato para rotas triviais (LOW). */
    private String fastModel = "claude-sonnet-5";

    /** Modelo default para o meio-termo (MEDIUM). */
    private String defaultModel = "claude-sonnet-5";

    /** SLO de groundedness (1..5, normalizado) exigido nas rotas de risco alto. */
    private double thresholdHigh = 0.90;

    /** SLO de groundedness exigido nas demais rotas. */
    private double thresholdDefault = 0.80;

    public String modelFor(RiskTier tier) {
        return switch (tier) {
            case HIGH -> strongModel;
            case LOW -> fastModel;
            case MEDIUM -> defaultModel;
        };
    }

    /** Limite de fundamentação por rota (SLO de qualidade — A03/A05). */
    public double thresholdFor(RiskTier tier) {
        return tier == RiskTier.HIGH ? thresholdHigh : thresholdDefault;
    }

    public String getStrongModel() {
        return strongModel;
    }

    public void setStrongModel(String strongModel) {
        this.strongModel = strongModel;
    }

    public String getFastModel() {
        return fastModel;
    }

    public void setFastModel(String fastModel) {
        this.fastModel = fastModel;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public double getThresholdHigh() {
        return thresholdHigh;
    }

    public void setThresholdHigh(double thresholdHigh) {
        this.thresholdHigh = thresholdHigh;
    }

    public double getThresholdDefault() {
        return thresholdDefault;
    }

    public void setThresholdDefault(double thresholdDefault) {
        this.thresholdDefault = thresholdDefault;
    }
}
