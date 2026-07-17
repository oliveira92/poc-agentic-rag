package com.example.agenticrag.quality;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Juiz determinístico (default). Não usa LLM: mede a sobreposição léxica entre os termos
 * relevantes da resposta e o contexto recuperado. Serve para o quality gate rodar no CI
 * sem chave de API ("Sem chave? roda em mock") e para os testes serem reprodutíveis.
 *
 * <p>Não substitui o juiz-LLM em produção — é um piso: se a resposta usa muitos termos que
 * NÃO aparecem no contexto, provavelmente inventou (nota baixa).
 */
@Component
@ConditionalOnProperty(name = "app.quality.judge", havingValue = "mock", matchIfMissing = true)
public class MockGroundednessEvaluator implements GroundednessEvaluator {

    private static final int MIN_TERM_LEN = 5;

    @Override
    public GroundednessResult evaluate(String question, String context, String answer) {
        Set<String> ctx = terms(context);
        Set<String> ans = terms(answer);
        if (ans.isEmpty()) {
            return new GroundednessResult(1, "Resposta vazia.", "mock");
        }
        if (ctx.isEmpty()) {
            return new GroundednessResult(1, "Sem contexto recuperado — resposta não pode estar fundamentada.", "mock");
        }
        long supported = ans.stream().filter(ctx::contains).count();
        double coverage = (double) supported / ans.size();
        int score = toScore(coverage);
        return new GroundednessResult(score,
                "Cobertura léxica de %.0f%% dos termos relevantes da resposta pelo contexto (heurística mock)."
                        .formatted(coverage * 100),
                "mock");
    }

    private static int toScore(double coverage) {
        if (coverage >= 0.80) return 5;
        if (coverage >= 0.60) return 4;
        if (coverage >= 0.40) return 3;
        if (coverage >= 0.20) return 2;
        return 1;
    }

    private static Set<String> terms(String text) {
        Set<String> out = new HashSet<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}/]+")) {
            if (raw.length() >= MIN_TERM_LEN) {
                out.add(raw);
            }
        }
        return out;
    }
}
