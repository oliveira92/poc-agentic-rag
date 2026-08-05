package com.example.agenticrag.security.scope;

import com.example.agenticrag.security.SecurityProperties;
import com.example.agenticrag.security.detector.TextFold;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Juiz de escopo determinístico (padrão). Duas perguntas, nesta ordem:
 *
 * <ol>
 *   <li><b>É uma tarefa que o agente faz?</b> ({@code off-task-patterns}) — "escreva um poema",
 *       "me dê uma orientação jurídica", "qual concorrente é melhor". Vem primeiro porque a
 *       tarefa errada continua errada mesmo cheia de vocabulário do domínio: "vale a pena
 *       processar a seguradora?" fala de seguro do começo ao fim e ainda assim é
 *       aconselhamento jurídico, que o agente não presta.</li>
 *   <li><b>Fala do que foi ingerido?</b> ({@code domain-terms} + o vocabulário do
 *       {@link ScopeContext}) — se nenhum termo do domínio aparece, está fora.</li>
 * </ol>
 *
 * <p>Sem vocabulário nenhum configurado, o juiz <b>se abstém</b> em vez de reprovar tudo:
 * um controle que barra 100% das perguntas por falta de config é um incidente, não uma defesa.
 *
 * <p>Limitação assumida e medida no dataset: é uma lista. Uma tarefa fora de escopo que ninguém
 * previu passa — daí o {@link LlmScopeJudge} existir para a rota de risco.
 */
@Component
@ConditionalOnProperty(name = "app.security.scope-judge", havingValue = "lexical", matchIfMissing = true)
public class LexicalScopeJudge implements ScopeJudge {

    /** Tamanho mínimo de token do contexto para virar vocabulário (abaixo disso é ruído). */
    private static final int MIN_CONTEXT_TERM = 5;

    private final List<String> domainTerms;
    private final List<Pattern> offTaskPatterns;

    public LexicalScopeJudge(SecurityProperties props) {
        SecurityProperties.Scope scope = props.scope();
        this.domainTerms = scope == null || scope.domainTerms() == null
                ? List.of()
                : scope.domainTerms().stream().map(TextFold::fold).filter(s -> !s.isBlank()).toList();
        this.offTaskPatterns = compile(scope == null ? null : scope.offTaskPatterns());
    }

    @Override
    public String name() {
        return "lexical";
    }

    @Override
    public ScopeVerdict judge(String question, ScopeContext context) {
        if (question == null || question.isBlank()) {
            return ScopeVerdict.abstain("pergunta vazia", name());
        }
        String q = TextFold.fold(question);

        for (Pattern p : offTaskPatterns) {
            if (p.matcher(q).find()) {
                return ScopeVerdict.out("tarefa fora do escopo do agente", name());
            }
        }

        // Termos de config podem ser expressões ("rate limit"), então casam por substring.
        for (String term : domainTerms) {
            if (q.contains(term)) {
                return ScopeVerdict.in("assunto coberto pelo conteúdo ingerido", name());
            }
        }

        List<String> contextTerms = contextVocabulary(context);

        // Sem saber o que a base contém, o juiz NÃO reprova. A lista global de termos descreve o
        // domínio do agente, não o conteúdo desta base: usá-la sozinha para dizer "fora de
        // escopo" barraria qualquer pergunta sobre um componente ainda não ingerido — e para
        // esse caso já existe resposta melhor (o aviso de 'ungrounded', que diz o que falta
        // ingerir em vez de recusar). A recusa por tarefa fora de escopo, essa sim, já aconteceu
        // acima e não depende de contexto nenhum.
        if (contextTerms.isEmpty()) {
            return ScopeVerdict.abstain("sem vocabulário do conteúdo ingerido para comparar", name());
        }
        Set<String> questionStems = stems(q);
        for (String term : contextTerms) {
            if (questionStems.contains(stem(term))) {
                return ScopeVerdict.in("assunto coberto pelo conteúdo ingerido", name());
            }
        }
        return ScopeVerdict.out("nenhum termo do conteúdo ingerido aparece na pergunta", name());
    }

    /**
     * Radical pobre: os {@value #MIN_CONTEXT_TERM} primeiros caracteres.
     *
     * <p>Sem isto o juiz reprovaria "como criar uma <i>cobrança</i>" contra uma base que fala em
     * "<i>cobranças</i>" — singular/plural viraria fora de escopo, e o controle passaria a
     * bloquear justamente as perguntas certas. Um stemmer de verdade (Snowball PT) seria melhor;
     * o prefixo resolve concordância e conjugação simples sem trazer dependência nova, e erra
     * para o lado seguro (aceitar), porque quem barra de fato é a lista de tarefas fora de escopo.
     */
    private static String stem(String token) {
        return token.length() <= MIN_CONTEXT_TERM ? token : token.substring(0, MIN_CONTEXT_TERM);
    }

    private static Set<String> stems(String foldedText) {
        Set<String> out = new HashSet<>();
        for (String token : foldedText.split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= MIN_CONTEXT_TERM) {
                out.add(stem(token));
            }
        }
        return out;
    }

    /**
     * Vocabulário derivado do que está de fato na base (id/descrição do componente, refs das
     * seções). É o que faz o juiz acompanhar a base sem ninguém reescrever a config a cada
     * componente novo.
     */
    private static List<String> contextVocabulary(ScopeContext context) {
        if (context == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        addTokens(out, context.componentId());
        addTokens(out, context.summary());
        for (String ref : context.knownRefs()) {
            addTokens(out, ref);
        }
        return out;
    }

    private static void addTokens(List<String> out, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String token : TextFold.fold(text).split("[^\\p{L}\\p{Nd}]+")) {
            if (token.length() >= MIN_CONTEXT_TERM) {
                out.add(token);
            }
        }
    }

    /** Regex inválida em config não pode derrubar o boot — vira log e some da lista. */
    private static List<Pattern> compile(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<Pattern> out = new ArrayList<>(raw.size());
        for (String r : raw) {
            try {
                out.add(Pattern.compile(TextFold.fold(r)));
            } catch (PatternSyntaxException e) {
                org.slf4j.LoggerFactory.getLogger(LexicalScopeJudge.class)
                        .warn("Padrão off-task inválido ignorado: '{}' ({})", r, e.getDescription());
            }
        }
        return out;
    }
}
