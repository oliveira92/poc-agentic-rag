package com.example.agenticrag.quality;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-as-judge (A03): um modelo "juiz" lê (pergunta, contexto, resposta) e devolve nota 1..5
 * + justificativa, seguindo uma rubrica de <b>groundedness</b>. Escala o que um humano não
 * daria conta (barato o bastante para CI/amostragem em produção).
 *
 * <p>Ativado com {@code app.quality.judge=llm} (requer ANTHROPIC_API_KEY). O juiz é um
 * {@link ChatClient} novo, <b>sem</b> a memória de conversa — a avaliação é sempre "cega" e
 * isolada. Lembre da calibração contra humano antes de confiar cego (a nota pode ter viés).
 */
@Component
@ConditionalOnProperty(name = "app.quality.judge", havingValue = "llm")
public class LlmGroundednessEvaluator implements GroundednessEvaluator {

    private static final Logger log = LoggerFactory.getLogger(LlmGroundednessEvaluator.class);

    private static final Pattern SCORE = Pattern.compile("(?i)score\\s*[:=]?\\s*([1-5])");

    private static final String RUBRIC = """
            Você é um AVALIADOR imparcial de GROUNDEDNESS (fundamentação) de respostas de RAG.
            Dada a PERGUNTA, o CONTEXTO recuperado e a RESPOSTA, avalie se CADA afirmação da
            resposta está apoiada no contexto. Ignore fluência e estilo — uma resposta bem
            escrita mas não apoiada no contexto deve receber nota BAIXA.

            Escala:
            5 = toda afirmação está no contexto.
            4 = quase tudo apoiado; detalhes menores fora.
            3 = parcialmente apoiado; afirmações relevantes sem base.
            2 = pouco apoiado; maior parte inventada.
            1 = não apoiado / alucinação.

            Responda EXATAMENTE neste formato (2 linhas):
            SCORE: <1-5>
            MOTIVO: <uma frase objetiva>
            """;

    private final ChatClient judge;

    public LlmGroundednessEvaluator(ChatClient.Builder builder) {
        // Cliente próprio do juiz: sem advisors (sem memória de conversa) → avaliação isolada.
        this.judge = builder.build();
    }

    @Override
    public GroundednessResult evaluate(String question, String context, String answer) {
        String user = """
                PERGUNTA:
                %s

                CONTEXTO:
                %s

                RESPOSTA:
                %s
                """.formatted(nz(question), nz(context), nz(answer));
        try {
            String raw = judge.prompt().system(RUBRIC).user(user).call().content();
            return parse(raw);
        } catch (Exception e) {
            log.warn("Falha ao avaliar groundedness via LLM: {}", e.getMessage());
            return new GroundednessResult(3, "Falha no juiz LLM: " + e.getMessage(), "llm:error");
        }
    }

    private static GroundednessResult parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return new GroundednessResult(3, "Juiz não retornou nota.", "llm");
        }
        Matcher m = SCORE.matcher(raw);
        int score = m.find() ? Integer.parseInt(m.group(1)) : 3;
        String reason = raw.replaceAll("(?is).*?motivo\\s*[:=]?\\s*", "").strip();
        if (reason.isBlank()) {
            reason = raw.strip();
        }
        return new GroundednessResult(score, reason, "llm");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
