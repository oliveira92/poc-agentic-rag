package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import com.example.agenticrag.security.SecuritySubject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SEC-10 — o recurso citado pertence a quem está perguntando?
 *
 * <p>Não implementa {@link Detector} de propósito: os outros controles decidem olhando só para
 * o texto, e este precisa de um insumo que o texto não tem — a identidade do requisitante.
 * Deixar isso explícito na assinatura evita a tentação de tratar autorização como mais um
 * regex, que é exatamente o erro que o caso A-05 do dataset expõe.
 *
 * <p>Comportamento: extrai identificadores de recurso da pergunta (SIN-8802, chg_abc123) e
 * bloqueia os que não estão na lista do requisitante. Sem identidade propagada
 * ({@link SecuritySubject#anonymous()}) o controle <b>não opina</b> — devolve vazio em vez de
 * bloquear tudo ou liberar tudo, e a lacuna fica registrada onde deve estar: na matriz de
 * evidências, não escondida num "return true".
 */
@Component
public class ResourceOwnershipDetector {

    /** Identificadores de domínio: SIN-8802, APOL-123, chg_abc123, cus_123. */
    private static final Pattern RESOURCE_ID = Pattern.compile(
            "\\b(?:[A-Z]{2,6}-\\d{2,}|[a-z]{2,5}_[A-Za-z0-9]{3,})\\b");

    public String controlId() {
        return "SEC-10";
    }

    public GuardCategory category() {
        return GuardCategory.RESOURCE_OWNERSHIP;
    }

    public String description() {
        return "Identificador de recurso citado na pergunta não pertence ao requisitante "
                + "(autorização a nível de objeto — BOLA/IDOR). Exige identidade propagada.";
    }

    /** Ocorrências de recursos que o requisitante NÃO possui. Vazio se não há identidade. */
    public List<DetectorMatch> find(String text, SecuritySubject subject) {
        if (text == null || text.isBlank() || subject == null || !subject.identified()) {
            return List.of();
        }
        List<DetectorMatch> out = new ArrayList<>();
        Matcher m = RESOURCE_ID.matcher(text);
        while (m.find()) {
            if (!subject.owns(m.group())) {
                out.add(new DetectorMatch("recurso_de_terceiro", m.start(), m.end()));
            }
        }
        return out;
    }
}
