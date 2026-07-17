package com.example.agenticrag.quality;

import com.example.agenticrag.advisor.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Guardrail de fidelidade DETERMINÍSTICO (A03 / HU-02), sempre ligado e de custo zero.
 *
 * <p>Extrai os endpoints citados no texto da resposta (ex.: {@code POST /v2/charges/{id}/refund})
 * e verifica se cada um aparece nas citações recuperadas. Se a resposta menciona um endpoint
 * que NÃO está nas fontes, é forte sinal de alucinação → marca {@code lowConfidence}. É o
 * complemento barato do juiz-LLM: pega o "mentiroso confiante" sem gastar uma chamada.
 *
 * <p>A comparação é <b>segmento a segmento e tolerante a placeholders</b>: um exemplo com id
 * concreto (<code>/v2/charges/chg_abc/refund</code>) casa com o endpoint canônico das fontes
 * (<code>/v2/charges/{id}/refund</code>) — evita o falso positivo de exemplos legítimos.
 */
@Component
public class CitationGroundingChecker {

    private static final Pattern ENDPOINT =
            // path sem '.'/':' finais para não engolir a pontuação da frase (ex.: ".../{id}.")
            Pattern.compile("\\b(GET|POST|PUT|PATCH|DELETE)\\s+(/[A-Za-z0-9_/{}-]+)");

    public record Result(boolean lowConfidence, List<String> unsupportedEndpoints) {
    }

    private record Endpoint(String method, String path) {
    }

    public Result check(String answer, List<Citation> citations) {
        List<String> unsupported = new ArrayList<>();
        if (answer == null || answer.isBlank()) {
            return new Result(false, unsupported);
        }
        List<Endpoint> supported = extractEndpoints(citationsText(citations));
        for (Endpoint ep : extractEndpoints(answer)) {
            if (supported.stream().noneMatch(s -> matches(ep, s))) {
                unsupported.add(ep.method() + " " + ep.path());
            }
        }
        return new Result(!unsupported.isEmpty(), unsupported);
    }

    private static List<Endpoint> extractEndpoints(String text) {
        List<Endpoint> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = ENDPOINT.matcher(text);
        while (m.find()) {
            out.add(new Endpoint(m.group(1).toUpperCase(Locale.ROOT), m.group(2).toLowerCase(Locale.ROOT)));
        }
        return out;
    }

    /** Mesmo método e mesmo path segmento-a-segmento, com {placeholder} casando id concreto. */
    private static boolean matches(Endpoint answer, Endpoint source) {
        if (!answer.method().equals(source.method())) {
            return false;
        }
        String[] a = answer.path().split("/");
        String[] s = source.path().split("/");
        if (a.length != s.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            boolean placeholder = isPlaceholder(a[i]) || isPlaceholder(s[i]);
            if (!placeholder && !a[i].equals(s[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlaceholder(String seg) {
        return seg.startsWith("{") && seg.endsWith("}");
    }

    /** Junta ref + snippet das citações para extrair os endpoints das fontes. */
    private static String citationsText(List<Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return "";
        }
        Set<String> parts = new LinkedHashSet<>();
        for (Citation c : citations) {
            if (c.ref() != null) {
                parts.add(c.ref());
            }
            if (c.snippet() != null) {
                parts.add(c.snippet());
            }
        }
        return String.join("\n", parts);
    }
}
