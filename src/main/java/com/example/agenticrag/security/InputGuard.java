package com.example.agenticrag.security;

import com.example.agenticrag.security.detector.BinaryContentDetector;
import com.example.agenticrag.security.detector.DetectorMatch;
import com.example.agenticrag.security.detector.PiiDetector;
import com.example.agenticrag.security.detector.PromptInjectionDetector;
import com.example.agenticrag.security.detector.ResourceOwnershipDetector;
import com.example.agenticrag.security.detector.SecretDetector;
import com.example.agenticrag.security.detector.ThirdPartyDataDetector;
import com.example.agenticrag.security.detector.UrlDetector;
import com.example.agenticrag.security.scope.ScopeContext;
import com.example.agenticrag.security.scope.ScopeJudge;
import com.example.agenticrag.security.scope.ScopeVerdict;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Guarda de <b>entrada</b>: tudo que roda entre a pergunta chegar e o prompt ser montado.
 *
 * <p>A ordem é do mais barato para o mais caro, mas <b>todos</b> os controles rodam: um único
 * regex custa microssegundos e o relatório fica mais rico. A exceção é o juiz de escopo, que
 * só entra depois dos determinísticos — na versão LLM ele custa uma chamada de modelo, e não
 * faz sentido pagar por ela para julgar o escopo de uma pergunta que já vai ser bloqueada por
 * conter uma tentativa de injeção.
 *
 * <p>Nada aqui está no system prompt. Uma pergunta bloqueada não chega ao modelo — não há o que
 * "convencer", que é a diferença entre um controle e uma recomendação.
 */
@Component
public class InputGuard {

    private final GuardEngine engine;
    private final SecurityProperties props;
    private final ScopeJudge scopeJudge;

    private final BinaryContentDetector binary;
    private final PromptInjectionDetector injection;
    private final ThirdPartyDataDetector thirdParty;
    private final UrlDetector url;
    private final SecretDetector secret;
    private final PiiDetector pii;
    private final ResourceOwnershipDetector ownership;

    public InputGuard(GuardEngine engine,
                      SecurityProperties props,
                      ScopeJudge scopeJudge,
                      BinaryContentDetector binary,
                      PromptInjectionDetector injection,
                      ThirdPartyDataDetector thirdParty,
                      UrlDetector url,
                      SecretDetector secret,
                      PiiDetector pii,
                      ResourceOwnershipDetector ownership) {
        this.engine = engine;
        this.props = props;
        this.scopeJudge = scopeJudge;
        this.binary = binary;
        this.injection = injection;
        this.thirdParty = thirdParty;
        this.url = url;
        this.secret = secret;
        this.pii = pii;
        this.ownership = ownership;
    }

    /** Nome do juiz ativo — vai para o relatório do dataset e para o {@code /security/controls}. */
    public String scopeJudgeName() {
        return scopeJudge.name();
    }

    public GuardDecision inspect(String question, ScopeContext context) {
        return inspect(question, context, SecuritySubject.anonymous());
    }

    /**
     * @param question texto cru vindo do cliente
     * @param context  o que a base contém (para o juiz de escopo)
     * @param subject  quem pergunta e o que pode ver (para o controle de autorização)
     */
    public GuardDecision inspect(String question, ScopeContext context, SecuritySubject subject) {
        if (!engine.enabled()) {
            return GuardDecision.allow(GuardStage.INPUT, question);
        }
        String text = question == null ? "" : question;

        List<GuardEngine.RawFinding> raw = new ArrayList<>(
                engine.scan(text, List.of(binary, injection, thirdParty, url, secret, pii)));

        oversize(text).ifPresent(raw::add);
        ownershipFinding(text, subject).ifPresent(raw::add);

        // Só paga o juiz se nada determinístico já mandou bloquear.
        if (!hasBlock(raw)) {
            scopeFinding(text, context).ifPresent(raw::add);
        }
        return engine.decide(GuardStage.INPUT, text, raw);
    }

    private boolean hasBlock(List<GuardEngine.RawFinding> raw) {
        return raw.stream()
                .anyMatch(f -> props.actionFor(GuardStage.INPUT, f.category()) == GuardAction.BLOCK);
    }

    /**
     * SEC-08 — teto de tamanho. Não é anti-alucinação, é anti-abuso: uma pergunta de 200 KB é
     * custo de token que alguém paga e janela onde uma instrução hostil se esconde no meio.
     */
    private Optional<GuardEngine.RawFinding> oversize(String text) {
        if (text.length() <= props.maxQuestionChars()) {
            return Optional.empty();
        }
        return Optional.of(new GuardEngine.RawFinding(
                "SEC-08", GuardCategory.OVERSIZE, "pergunta_muito_longa", List.of(),
                "Pergunta acima de " + props.maxQuestionChars() + " caracteres."));
    }

    private Optional<GuardEngine.RawFinding> ownershipFinding(String text, SecuritySubject subject) {
        List<DetectorMatch> matches = ownership.find(text, subject);
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GuardEngine.RawFinding(
                ownership.controlId(), ownership.category(), matches.get(0).label(), matches,
                ownership.description()));
    }

    private Optional<GuardEngine.RawFinding> scopeFinding(String text, ScopeContext context) {
        ScopeVerdict verdict = scopeJudge.judge(text, context);
        if (verdict.inScope()) {
            return Optional.empty();
        }
        return Optional.of(new GuardEngine.RawFinding(
                "SEC-07", GuardCategory.OUT_OF_SCOPE, "fora_de_escopo", List.of(),
                verdict.reason() + " (juiz: " + verdict.judge() + ")"));
    }
}
