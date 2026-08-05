package com.example.agenticrag.security.detector;

import com.example.agenticrag.security.GuardCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SEC-09 — a resposta está devolvendo o prompt de sistema.
 *
 * <p>É o controle de <b>saída</b> correspondente ao SEC-04 de entrada, e existe porque os dois
 * falham de formas diferentes: o detector de injeção erra quando o atacante inventa uma
 * formulação nova; este erra só se o modelo parafrasear o prompt inteiro. Um ataque que passe
 * pela entrada ainda precisa passar por aqui — e um pedido que nem parecia ataque
 * ("resuma nossa conversa desde o início") também é pego.
 *
 * <p>Método: <i>shingles</i> de {@value #SHINGLE_WORDS} palavras. Uma sequência longa idêntica
 * é reprodução; palavras isoladas em comum são só português. O prompt de sistema é o insumo,
 * então o controle acompanha automaticamente qualquer edição no prompt — sem lista para manter.
 */
@Component
public class SystemPromptLeakDetector {

    /**
     * Tamanho da janela. Curto demais ({@literal <}5) acusa frase comum do domínio; longo demais
     * ({@literal >}10) só pega cópia literal e perde a paráfrase parcial.
     */
    private static final int SHINGLE_WORDS = 7;

    /** Prompts curtos não geram shingle suficiente para uma conclusão confiável. */
    private static final int MIN_WORDS = SHINGLE_WORDS * 2;

    public String controlId() {
        return "SEC-09";
    }

    public GuardCategory category() {
        return GuardCategory.SYSTEM_PROMPT_LEAK;
    }

    public String description() {
        return "Resposta reproduz uma sequência de " + SHINGLE_WORDS
                + " palavras do prompt de sistema (extração de instruções bem-sucedida).";
    }

    /**
     * @param answer       texto gerado pelo modelo
     * @param systemPrompt prompt de sistema usado na chamada
     * @return uma ocorrência por shingle compartilhado (posição no {@code answer})
     */
    public List<DetectorMatch> find(String answer, String systemPrompt) {
        if (answer == null || answer.isBlank() || systemPrompt == null || systemPrompt.isBlank()) {
            return List.of();
        }
        List<Word> answerWords = words(answer);
        List<Word> promptWords = words(systemPrompt);
        if (promptWords.size() < MIN_WORDS || answerWords.size() < SHINGLE_WORDS) {
            return List.of();
        }
        Set<String> promptShingles = new HashSet<>();
        for (int i = 0; i + SHINGLE_WORDS <= promptWords.size(); i++) {
            promptShingles.add(join(promptWords, i));
        }
        List<DetectorMatch> out = new ArrayList<>();
        for (int i = 0; i + SHINGLE_WORDS <= answerWords.size(); i++) {
            if (promptShingles.contains(join(answerWords, i))) {
                out.add(new DetectorMatch("system_prompt",
                        answerWords.get(i).start(),
                        answerWords.get(i + SHINGLE_WORDS - 1).end()));
            }
        }
        return out;
    }

    private static String join(List<Word> words, int from) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < from + SHINGLE_WORDS; i++) {
            sb.append(words.get(i).text()).append(' ');
        }
        return sb.toString();
    }

    /** Palavra normalizada + posição no texto original (para o Redactor, quando a ação é MASK). */
    private record Word(String text, int start, int end) {
    }

    private static List<Word> words(String text) {
        String folded = TextFold.fold(text);
        List<Word> out = new ArrayList<>();
        int i = 0;
        while (i < folded.length()) {
            if (!Character.isLetterOrDigit(folded.charAt(i))) {
                i++;
                continue;
            }
            int start = i;
            while (i < folded.length() && Character.isLetterOrDigit(folded.charAt(i))) {
                i++;
            }
            out.add(new Word(folded.substring(start, i), start, i));
        }
        return out;
    }
}
