package com.example.agenticrag.quality;

import com.example.agenticrag.routing.RiskTier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Carrega o golden set versionado de {@code classpath:golden/{componentId}.csv}.
 *
 * <p>O golden set é versionado JUNTO com o prompt e o modelo (A05): qualidade = f(prompt,
 * golden, modelo). Trocou um dos três? É uma versão nova e um novo teste. Aqui ele mora no
 * repositório (fonte da verdade do gate); em produção pode virar um dataset no Langfuse.
 *
 * <p>CSV: {@code id,componentId,question,expectedRef,risk,kind} — campos podem vir entre aspas
 * duplas (perguntas costumam ter vírgula).
 */
@Component
public class GoldenSet {

    public List<GoldenCase> load(String componentId) {
        String path = "golden/" + componentId + ".csv";
        ClassPathResource resource = new ClassPathResource(path);
        if (!resource.exists()) {
            throw new IllegalArgumentException("Golden set não encontrado: " + path);
        }
        List<GoldenCase> cases = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            boolean header = true;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                if (header) {
                    header = false;
                    continue;
                }
                List<String> f = parseCsvLine(line);
                if (f.size() < 6) {
                    continue;
                }
                cases.add(new GoldenCase(
                        f.get(0).trim(),
                        f.get(1).trim(),
                        f.get(2).trim(),
                        f.get(3).trim(),
                        parseRisk(f.get(4)),
                        f.get(5).trim()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler golden set " + path, e);
        }
        return cases;
    }

    private static RiskTier parseRisk(String s) {
        try {
            return RiskTier.valueOf(s.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return RiskTier.MEDIUM;
        }
    }

    /** Parser CSV mínimo com suporte a campos entre aspas duplas e "" como aspa escapada. */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }
}
