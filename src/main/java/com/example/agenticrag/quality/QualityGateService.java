package com.example.agenticrag.quality;

import com.example.agenticrag.advisor.Citation;
import com.example.agenticrag.advisor.ComponentAdvisorService;
import com.example.agenticrag.routing.RoutingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Executa o <b>quality gate de recuperação</b> (A05 — "O Portão") contra o golden set.
 *
 * <p>Determinístico e sem efeito colateral: para cada caso, roda a recuperação
 * (embeddings locais + pgvector) e verifica se a fonte esperada apareceu no top-K. Casos
 * de armadilha (fora de escopo) passam quando o sistema NÃO recupera nada com confiança —
 * ou seja, não daria margem para o modelo inventar. Não gera resposta nem persiste rascunho,
 * então pode rodar no CI a cada PR que mexe em prompt/golden/modelo.
 *
 * <p>A camada de groundedness por juiz-LLM (A03) é complementar e roda por amostragem em
 * produção; aqui o gate barra a regressão de RECUPERAÇÃO — a causa raiz da maioria das
 * alucinações ("groundedness baixo? olhe o retrieval antes de culpar o modelo").
 */
@Service
public class QualityGateService {

    private static final Logger log = LoggerFactory.getLogger(QualityGateService.class);

    private final ComponentAdvisorService advisor;
    private final GoldenSet goldenSet;
    private final RoutingProperties routing;

    /** Score abaixo do qual consideramos que "nada foi recuperado com confiança" (armadilhas). */
    @Value("${app.quality.gate.trap-floor:0.35}")
    private double trapFloor;

    /** Taxa mínima de aprovação geral para o gate passar (tolera as armadilhas de over-retrieval). */
    @Value("${app.quality.gate.min-pass-rate:0.75}")
    private double minPassRate;

    /** Top-K usado na avaliação de recuperação. */
    @Value("${app.quality.gate.top-k:6}")
    private int topK;

    public QualityGateService(ComponentAdvisorService advisor, GoldenSet goldenSet, RoutingProperties routing) {
        this.advisor = advisor;
        this.goldenSet = goldenSet;
        this.routing = routing;
    }

    public QualityGate.Report run(String componentId) {
        List<GoldenCase> cases = goldenSet.load(componentId);
        List<QualityGate.CaseEvaluation> evals = new ArrayList<>(cases.size());

        for (GoldenCase gc : cases) {
            List<Citation> citations = advisor.retrieve(componentId, gc.question(), topK);
            double topScore = topScore(citations);
            double threshold = routing.thresholdFor(gc.risk());

            boolean hit = gc.isTrap()
                    ? (citations.isEmpty() || topScore < trapFloor)          // não recuperou → não inventa
                    : citationsContain(citations, gc.expectedRef());          // recuperou a fonte esperada

            // Sem geração no gate: groundedness fica a cargo da amostragem em produção (A03).
            boolean passed = hit;
            evals.add(new QualityGate.CaseEvaluation(gc.id(), gc.risk(), hit, null, threshold, passed));
        }

        QualityGate.Report report = QualityGate.decide(evals, minPassRate);
        log.info("Quality gate {} — {}/{} casos, hitRate={}%, gate={}",
                componentId, report.passed(), report.total(),
                Math.round(report.hitRate() * 100), report.gatePassed() ? "PASS" : "FAIL");
        return report;
    }

    private static double topScore(List<Citation> citations) {
        if (citations.isEmpty() || citations.get(0).score() == null) {
            return 0.0;
        }
        return citations.get(0).score();
    }

    private static boolean citationsContain(List<Citation> citations, String expectedRef) {
        String needle = expectedRef.toLowerCase(Locale.ROOT).trim();
        for (Citation c : citations) {
            String ref = c.ref() == null ? "" : c.ref().toLowerCase(Locale.ROOT);
            String snippet = c.snippet() == null ? "" : c.snippet().toLowerCase(Locale.ROOT);
            if (ref.contains(needle) || snippet.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
