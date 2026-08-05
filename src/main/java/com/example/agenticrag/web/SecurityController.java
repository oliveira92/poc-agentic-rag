package com.example.agenticrag.web;

import com.example.agenticrag.security.SecurityControlsService;
import com.example.agenticrag.security.dataset.SecurityCase;
import com.example.agenticrag.security.dataset.SecurityDataset;
import com.example.agenticrag.security.dataset.SecurityDatasetProperties;
import com.example.agenticrag.security.dataset.SecurityEvaluationReport;
import com.example.agenticrag.security.dataset.SecurityEvaluationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Superfície de evidência do M04: o que está implementado e o que os datasets dizem sobre isso.
 *
 * <p>Ser endpoint, e não um relatório gerado no build, é o ponto: a mentoria roda contra a
 * aplicação de pé, com a política que está realmente carregada. Relatório em arquivo prova o
 * que era verdade quando alguém rodou; endpoint prova o que é verdade agora.
 */
@RestController
@RequestMapping("/api/v1/security")
public class SecurityController {

    private final SecurityControlsService controls;
    private final SecurityEvaluationService evaluation;
    private final SecurityDataset dataset;
    private final SecurityDatasetProperties datasetProps;

    public SecurityController(SecurityControlsService controls,
                              SecurityEvaluationService evaluation,
                              SecurityDataset dataset,
                              SecurityDatasetProperties datasetProps) {
        this.controls = controls;
        this.evaluation = evaluation;
        this.dataset = dataset;
        this.datasetProps = datasetProps;
    }

    /** Estado da camada + inventário dos controles com a ação de cada estágio. */
    @GetMapping("/controls")
    public Map<String, Object> controls() {
        return Map.of(
                "status", controls.status(),
                "controls", controls.list());
    }

    /** Cenários disponíveis (id, nome e quantos casos cada um tem). */
    @GetMapping("/scenarios")
    public List<Map<String, Object>> scenarios() {
        return datasetProps.scenarios().stream()
                .<Map<String, Object>>map(s -> Map.of(
                        "name", s.name(),
                        "label", s.label(),
                        "cases", dataset.load(s).size(),
                        "isDefault", s.name().equalsIgnoreCase(datasetProps.defaultScenario())))
                .toList();
    }

    /**
     * Roda <b>todos</b> os cenários e devolve linha de base × protegida para cada um.
     *
     * <p>Todos, e não só o principal, porque a afirmação que interessa não é "passa no meu
     * domínio" — é "os controles não dependem do domínio". Isso só aparece com mais de um.
     *
     * <p>422 quando qualquer cenário tem falso positivo ou deixa passar ataque: mesmo padrão do
     * quality gate — o corpo traz os relatórios nos dois casos, porque "reprovou" e "por que
     * reprovou" precisam chegar juntos.
     */
    @PostMapping("/evaluate")
    public ResponseEntity<List<SecurityEvaluationReport>> evaluate() {
        List<SecurityEvaluationReport> reports = evaluation.runAll();
        boolean clean = reports.stream().allMatch(r -> r.protectedRun().clean());
        return ResponseEntity.status(clean ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(reports);
    }

    /** Um cenário só ({@code componentes} | {@code a05-seguros}). */
    @PostMapping("/evaluate/{scenario}")
    public ResponseEntity<SecurityEvaluationReport> evaluateOne(@PathVariable String scenario) {
        SecurityEvaluationReport report = evaluation.run(scenario);
        return ResponseEntity.status(report.protectedRun().clean()
                        ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY)
                .body(report);
    }

    /** Casos de um cenário, sem executar os controles — para conferir o que está sendo medido. */
    @GetMapping("/dataset/{scenario}")
    public List<SecurityCase> dataset(@PathVariable String scenario) {
        return dataset.load(datasetProps.byName(scenario));
    }
}
