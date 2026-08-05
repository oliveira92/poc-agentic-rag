package com.example.agenticrag.security;

import com.example.agenticrag.security.detector.BinaryContentDetector;
import com.example.agenticrag.security.detector.PiiDetector;
import com.example.agenticrag.security.detector.PromptInjectionDetector;
import com.example.agenticrag.security.detector.SecretDetector;
import com.example.agenticrag.security.scope.ScopeContext;
import com.example.agenticrag.security.scope.ScopeJudge;
import com.example.agenticrag.security.scope.ScopeVerdict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda de <b>ingestão</b>: o controle mais severo dos três, porque é o único cujo erro é
 * <b>permanente</b>.
 *
 * <p>Uma pergunta ruim afeta uma conversa. Um documento ruim vira embedding, entra no índice e
 * volta como "fonte confiável" em toda resposta futura — com citação, com aparência de
 * autoridade. Segredo indexado é segredo que o RAG passa a distribuir sob demanda; PII indexada
 * é titular exposto a quem perguntar direito; conteúdo fora de escopo (a receita de bolo) é
 * ruído que compete com a fonte primária no top-K. Daí a política padrão aqui ser BLOCK em
 * tudo, sem MASK: na dúvida, o documento não entra — reingerir é barato, despoluir o índice não.
 *
 * <p>A injeção também é checada na ingestão, e não só na entrada: um README com "ignore suas
 * instruções" hospedado em repositório de terceiro é injeção indireta, o vetor que não passa
 * pelo teclado do usuário.
 */
@Component
public class IngestionGuard {

    private final GuardEngine engine;
    private final SecurityProperties props;
    private final ScopeJudge scopeJudge;

    private final BinaryContentDetector binary;
    private final SecretDetector secret;
    private final PiiDetector pii;
    private final PromptInjectionDetector injection;

    public IngestionGuard(GuardEngine engine,
                          SecurityProperties props,
                          ScopeJudge scopeJudge,
                          BinaryContentDetector binary,
                          SecretDetector secret,
                          PiiDetector pii,
                          PromptInjectionDetector injection) {
        this.engine = engine;
        this.props = props;
        this.scopeJudge = scopeJudge;
        this.binary = binary;
        this.secret = secret;
        this.pii = pii;
        this.injection = injection;
    }

    /**
     * @param content documento cru (README, texto do portal) prestes a virar embedding
     * @param context descrição do que a base cobre — o juiz decide se o documento pertence a ela
     */
    public GuardDecision inspect(String content, ScopeContext context) {
        if (!engine.enabled()) {
            return GuardDecision.allow(GuardStage.INGESTION, content);
        }
        String text = content == null ? "" : content;

        List<GuardEngine.RawFinding> raw = new ArrayList<>(
                engine.scan(text, List.of(binary, secret, pii, injection)));

        if (text.length() > props.maxDocumentChars()) {
            raw.add(new GuardEngine.RawFinding("SEC-08", GuardCategory.OVERSIZE,
                    "documento_muito_longo", List.of(),
                    "Documento acima de " + props.maxDocumentChars() + " caracteres."));
        }
        scopeFinding(text, context).ifPresent(raw::add);

        return engine.decide(GuardStage.INGESTION, text, raw);
    }

    /**
     * O juiz recebe uma <b>amostra</b> do documento, não ele inteiro: o veredito de assunto se
     * forma nos primeiros parágrafos, e mandar 200 KB para um juiz LLM custaria mais que a
     * ingestão que ele está protegendo.
     *
     * <p>Sem contexto, o juiz <b>não é consultado</b>. É o caso da varredura por documento, em
     * que a moldura de escopo já foi aplicada ao README inteiro e cada pedaço, isolado, não tem
     * como ser julgado — perguntar aqui só produziria um "fora de escopo" sem significado.
     */
    private Optional<GuardEngine.RawFinding> scopeFinding(String text, ScopeContext context) {
        if (context == null) {
            return Optional.empty();
        }
        String sample = text.length() > 2000 ? text.substring(0, 2000) : text;
        ScopeVerdict verdict = scopeJudge.judge(sample, context);
        if (verdict.inScope()) {
            return Optional.empty();
        }
        return Optional.of(new GuardEngine.RawFinding(
                "SEC-07", GuardCategory.OUT_OF_SCOPE, "conteudo_fora_de_escopo", List.of(),
                verdict.reason() + " (juiz: " + verdict.judge() + ")"));
    }
}
