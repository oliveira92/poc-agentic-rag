package com.example.agenticrag.security.dataset;

import com.example.agenticrag.security.GuardAction;
import com.example.agenticrag.security.GuardDecision;
import com.example.agenticrag.security.InputGuard;
import com.example.agenticrag.security.SecuritySubject;
import com.example.agenticrag.security.scope.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Executa o dataset de segurança contra a camada de guardrails e produz a evidência.
 *
 * <p>Duas execuções por caso:
 * <ul>
 *   <li><b>linha de base</b> — sistema sem controles. Não é uma medição, é a definição: sem
 *       controle, tudo passa. Existe para dar contraste e denominador, e está no relatório
 *       rotulada como tal em vez de sugerir um experimento que não houve;</li>
 *   <li><b>protegida</b> — o pipeline real do {@code /advise}: os mesmos detectores, a mesma
 *       política de {@code application.yml}, o mesmo juiz de escopo. Não há caminho de teste
 *       paralelo — se o controle mudar, este número muda junto.</li>
 * </ul>
 *
 * <p>MASK conta como <b>atendido</b>, não como bloqueado. É a decisão de projeto que o dataset
 * mede: uma pergunta que traz PII continua sendo respondida, com o dado ofuscado antes de chegar
 * ao modelo. Contabilizar MASK como bloqueio esconderia exatamente o que se quer provar.
 *
 * <p>Rodar <b>mais de um cenário</b> ({@link #runAll()}) é o que separa "os controles funcionam
 * nestes 17 casos" de "os controles não dependem do domínio": o cenário de componentes e o de
 * atendimento em seguros passam pelo mesmo código, sem uma linha específica de domínio.
 */
@Service
public class SecurityEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(SecurityEvaluationService.class);

    private final SecurityDataset dataset;
    private final SecurityDatasetProperties props;
    private final InputGuard inputGuard;

    public SecurityEvaluationService(SecurityDataset dataset,
                                     SecurityDatasetProperties props,
                                     InputGuard inputGuard) {
        this.dataset = dataset;
        this.props = props;
        this.inputGuard = inputGuard;
    }

    /** Cenário padrão ({@code app.security-dataset.default-scenario}). */
    public SecurityEvaluationReport run() {
        return run(props.primary());
    }

    public SecurityEvaluationReport run(String scenarioName) {
        return run(props.byName(scenarioName));
    }

    /** Todos os cenários configurados, na ordem em que aparecem. */
    public List<SecurityEvaluationReport> runAll() {
        return props.scenarios().stream().map(this::run).toList();
    }

    public SecurityEvaluationReport run(SecurityDatasetProperties.Scenario scenario) {
        ScopeContext context = ScopeContext.of(scenario.name(), scenario.summary());
        SecuritySubject subject = subjectOf(scenario);

        List<SecurityEvaluationReport.CaseResult> results = new ArrayList<>();
        for (SecurityCase c : dataset.load(scenario)) {
            results.add(toResult(c, inputGuard.inspect(c.question(), context, subject)));
        }

        SecurityEvaluationReport.Metrics baseline = metrics(results, true);
        SecurityEvaluationReport.Metrics guarded = metrics(results, false);
        log.info("Dataset de segurança '{}': protegida bloqueou {}/{} ruins, "
                        + "com {}/{} legítimos barrados (juiz={})",
                scenario.name(), guarded.badBlocked(), guarded.badCases(),
                guarded.legitimateBlocked(), guarded.legitimateCases(), inputGuard.scopeJudgeName());

        return new SecurityEvaluationReport(scenario.name(), scenario.label(),
                inputGuard.scopeJudgeName(), baseline, guarded, List.copyOf(results));
    }

    private static SecuritySubject subjectOf(SecurityDatasetProperties.Scenario scenario) {
        if (scenario.subjectId() == null || scenario.subjectId().isBlank()) {
            return SecuritySubject.anonymous();
        }
        return new SecuritySubject(scenario.subjectId(),
                Set.copyOf(scenario.ownedResources() == null ? List.of() : scenario.ownedResources()));
    }

    private static SecurityEvaluationReport.CaseResult toResult(SecurityCase c, GuardDecision d) {
        boolean blocked = d.blocked();
        String outcome = c.shouldBlock()
                ? (blocked ? "TP" : "FN")
                : (blocked ? "FP" : "TN");
        return new SecurityEvaluationReport.CaseResult(
                c.id(), c.type(), c.question(), c.shouldBlock(),
                GuardAction.ALLOW,          // linha de base: sem controles, nada é barrado
                d.action(),
                d.controlIds(),
                d.categories().stream().map(Enum::name).toList(),
                outcome,
                note(c, d, outcome));
    }

    /** A frase que um auditor lê antes de perguntar "e esse caso aí?". */
    private static String note(SecurityCase c, GuardDecision d, String outcome) {
        if ("FP".equals(outcome)) {
            return "FALSO POSITIVO: caso legítimo barrado pelos controles " + d.controlIds()
                    + " — revisar política antes de subir.";
        }
        if ("FN".equals(outcome)) {
            return "NÃO DETECTADO: risco residual — nenhum controle determinístico cobre esta "
                    + "formulação (mitigação prevista: juiz LLM na rota de risco).";
        }
        if (d.masked()) {
            return "Atendido com ofuscação (" + String.join(", ", d.controlIds())
                    + "): o dado sensível não chega ao modelo, quem perguntou continua sendo respondido.";
        }
        if (c.legitimate()) {
            return "Preservado sem intervenção.";
        }
        return "Barrado por " + String.join(", ", d.controlIds()) + ".";
    }

    private static SecurityEvaluationReport.Metrics metrics(
            List<SecurityEvaluationReport.CaseResult> results, boolean baseline) {

        int bad = 0;
        int legit = 0;
        int badBlocked = 0;
        int legitBlocked = 0;
        int legitMasked = 0;
        for (SecurityEvaluationReport.CaseResult r : results) {
            GuardAction action = baseline ? r.baselineAction() : r.action();
            boolean blocked = action == GuardAction.BLOCK;
            if (r.shouldBlock()) {
                bad++;
                if (blocked) {
                    badBlocked++;
                }
            } else {
                legit++;
                if (blocked) {
                    legitBlocked++;
                } else if (action == GuardAction.MASK) {
                    legitMasked++;
                }
            }
        }
        return new SecurityEvaluationReport.Metrics(results.size(), bad, legit,
                badBlocked, bad - badBlocked, legitBlocked, legit - legitBlocked, legitMasked);
    }
}
