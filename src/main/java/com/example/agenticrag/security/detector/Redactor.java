package com.example.agenticrag.security.detector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aplica a ofuscação sobre os trechos casados, substituindo cada um por {@code [REDACTED:rótulo]}.
 *
 * <p>Dois cuidados que parecem detalhe e não são:
 * <ul>
 *   <li><b>ordem decrescente</b>: substituir do fim para o começo mantém os offsets dos casamentos
 *       ainda não aplicados válidos (substituir do início desloca todos os demais);</li>
 *   <li><b>merge de sobreposição</b>: detectores diferentes casam o mesmo trecho (um telefone
 *       dentro de um CPF mal formatado, um token dentro de uma URI com senha). Sem unir os
 *       intervalos, a segunda substituição cairia no meio do {@code [REDACTED:...]} da primeira.</li>
 * </ul>
 * O rótulo preservado é o do primeiro casamento do grupo — o suficiente para auditar sem
 * reconstruir o dado.
 */
public final class Redactor {

    private Redactor() {
    }

    public static String redact(String text, List<DetectorMatch> matches) {
        if (text == null || text.isEmpty() || matches.isEmpty()) {
            return text;
        }
        List<DetectorMatch> merged = merge(matches, text.length());
        StringBuilder sb = new StringBuilder(text);
        for (int i = merged.size() - 1; i >= 0; i--) {
            DetectorMatch m = merged.get(i);
            sb.replace(m.start(), m.end(), "[REDACTED:" + m.label() + "]");
        }
        return sb.toString();
    }

    /** Une intervalos que se tocam ou se sobrepõem; descarta os que caem fora do texto. */
    static List<DetectorMatch> merge(List<DetectorMatch> matches, int textLength) {
        List<DetectorMatch> sorted = new ArrayList<>(matches.size());
        for (DetectorMatch m : matches) {
            if (m.start() < textLength && m.end() <= textLength && m.end() > m.start()) {
                sorted.add(m);
            }
        }
        sorted.sort(Comparator.comparingInt(DetectorMatch::start).thenComparingInt(DetectorMatch::end));

        List<DetectorMatch> out = new ArrayList<>(sorted.size());
        for (DetectorMatch m : sorted) {
            if (out.isEmpty()) {
                out.add(m);
                continue;
            }
            DetectorMatch last = out.get(out.size() - 1);
            if (m.start() <= last.end()) {
                out.set(out.size() - 1,
                        new DetectorMatch(last.label(), last.start(), Math.max(last.end(), m.end())));
            } else {
                out.add(m);
            }
        }
        return out;
    }
}
