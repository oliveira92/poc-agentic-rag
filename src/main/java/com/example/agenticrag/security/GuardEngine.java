package com.example.agenticrag.security;

import com.example.agenticrag.observability.RagMetrics;
import com.example.agenticrag.security.detector.Detector;
import com.example.agenticrag.security.detector.DetectorMatch;
import com.example.agenticrag.security.detector.Redactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * O motor comum dos três guards: recebe achados crus, aplica a <b>política</b> do estágio
 * ({@link SecurityProperties#actionFor}) e produz o veredito com o texto já sanitizado.
 *
 * <p>Concentrar isto num lugar só é o que garante que a política não diverge entre estágios: se
 * cada guard decidisse por conta, a ação de PII na entrada e na ingestão viveriam em dois
 * {@code if} que alguém edita separado e ninguém revisa junto.
 *
 * <p>Os detectores rodam <b>todos</b>, mesmo quando o primeiro já mandou bloquear. Custa alguns
 * microssegundos e paga em evidência: o relatório mostra tudo que a requisição violou, não
 * apenas o primeiro achado — e é isso que permite dizer "este caso foi pego por 3 controles
 * independentes" na hora de discutir risco residual.
 */
@Component
public class GuardEngine {

    private static final Logger log = LoggerFactory.getLogger(GuardEngine.class);

    private final SecurityProperties props;
    private final RagMetrics metrics;

    public GuardEngine(SecurityProperties props, RagMetrics metrics) {
        this.props = props;
        this.metrics = metrics;
    }

    /**
     * Achado antes da política: o que foi encontrado, sem ainda dizer o que fazer.
     *
     * @param controlId id do controle (SEC-xx)
     * @param category  categoria de risco
     * @param label     rótulo do casamento
     * @param matches   posições no texto (para mascarar); vazio quando o achado não é textual
     * @param detail    frase segura para exibir
     */
    public record RawFinding(String controlId, GuardCategory category, String label,
                             List<DetectorMatch> matches, String detail) {

        public RawFinding {
            matches = matches == null ? List.of() : List.copyOf(matches);
        }
    }

    public boolean enabled() {
        return props.enabled();
    }

    /** Roda os detectores textuais do estágio e devolve os achados crus. */
    public List<RawFinding> scan(String text, List<? extends Detector> detectors) {
        List<RawFinding> out = new ArrayList<>();
        for (Detector d : detectors) {
            List<DetectorMatch> matches;
            try {
                matches = d.find(text);
            } catch (RuntimeException e) {
                // Um detector com defeito não pode ser um bypass silencioso: no fail-closed
                // ele vira um achado de bloqueio, e o incidente aparece.
                log.error("Detector {} falhou: {}", d.controlId(), e.toString());
                if (props.failClosed()) {
                    out.add(new RawFinding(d.controlId(), d.category(), "erro_interno", List.of(),
                            "Falha ao executar o controle (fail-closed)."));
                }
                continue;
            }
            if (!matches.isEmpty()) {
                out.add(new RawFinding(d.controlId(), d.category(), matches.get(0).label(),
                        matches, d.description()));
            }
        }
        return out;
    }

    /**
     * Aplica a política do estágio: resolve a ação de cada achado, mascara o que for MASK e
     * devolve a ação mais severa.
     */
    public GuardDecision decide(GuardStage stage, String text, List<RawFinding> raw) {
        long t0 = System.nanoTime();
        try {
            if (!props.enabled() || raw.isEmpty()) {
                return GuardDecision.allow(stage, text);
            }
            List<GuardFinding> findings = new ArrayList<>(raw.size());
            List<DetectorMatch> toMask = new ArrayList<>();
            GuardAction overall = GuardAction.ALLOW;

            for (RawFinding f : raw) {
                GuardAction action = props.actionFor(stage, f.category());
                if (action == GuardAction.ALLOW) {
                    continue;                          // categoria não é tratada neste estágio
                }
                findings.add(new GuardFinding(f.controlId(), f.category(), action, f.label(),
                        Math.max(f.matches().size(), 1), f.detail()));
                if (action == GuardAction.MASK) {
                    toMask.addAll(f.matches());
                }
                overall = GuardAction.max(overall, action);
                metrics.recordGuard(stage.name(), action.name(), f.controlId());
            }

            if (overall == GuardAction.ALLOW) {
                return GuardDecision.allow(stage, text);
            }
            // Num BLOCK o texto não segue viagem, então mascarar seria trabalho jogado fora.
            String result = overall == GuardAction.MASK ? Redactor.redact(text, toMask) : text;
            return new GuardDecision(stage, overall, result, List.copyOf(findings));
        } finally {
            metrics.recordGuardLatency(stage.name(), System.nanoTime() - t0);
        }
    }
}
