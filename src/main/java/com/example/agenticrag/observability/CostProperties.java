package com.example.agenticrag.observability;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.Locale;
import java.util.Map;

/**
 * Preços para estimar o custo por chamada (custo mora nos tokens — A04). Suporta preço
 * <b>por família de modelo</b> (chave casa por substring do id: {@code haiku}, {@code sonnet},
 * {@code opus}), com fallback para um preço global. É isso que deixa o custo refletir o
 * roteamento por risco: rota barata (Haiku) custa uma fração da rota forte (Sonnet/Opus).
 *
 * <pre>
 * app.cost:
 *   currency: USD
 *   per-family:              # preço por 1k tokens (ajuste ao seu contrato)
 *     haiku:  { input: 0.001, output: 0.005 }
 *     sonnet: { input: 0.003, output: 0.015 }
 *     opus:   { input: 0.015, output: 0.075 }
 *   input-per-1k: 0.0        # fallback global quando nenhuma família casa
 *   output-per-1k: 0.0
 * </pre>
 */
@ConfigurationProperties(prefix = "app.cost")
public record CostProperties(
        @DefaultValue("USD") String currency,
        @DefaultValue("0.0") double inputPer1k,
        @DefaultValue("0.0") double outputPer1k,
        @DefaultValue Map<String, Price> perFamily) {

    /** Preço por 1k tokens de entrada/saída. */
    public record Price(double input, double output) {
    }

    /** Preço da família que casa com o id do modelo, ou o global como fallback. */
    public Price priceFor(String model) {
        if (model != null && perFamily != null) {
            String m = model.toLowerCase(Locale.ROOT);
            for (Map.Entry<String, Price> e : perFamily.entrySet()) {
                if (m.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    return e.getValue();
                }
            }
        }
        return new Price(inputPer1k, outputPer1k);
    }

    /** Custo estimado (moeda de {@link #currency()}) para os tokens de uma chamada. */
    public double estimate(Integer inputTokens, Integer outputTokens, String model) {
        Price p = priceFor(model);
        double in = inputTokens == null ? 0 : inputTokens;
        double out = outputTokens == null ? 0 : outputTokens;
        return in / 1000.0 * p.input() + out / 1000.0 * p.output();
    }
}
