package com.example.agenticrag.web;

import com.example.agenticrag.quality.QualityGate;
import com.example.agenticrag.quality.QualityGateService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Camada 5 (Governança): expõe o quality gate ("O Portão") sob demanda.
 *
 * <p>Roda o golden set do componente pela recuperação e devolve o relatório com a decisão.
 * Quando o gate FALHA, responde HTTP 422 — o que permite ao GitHub Actions barrar o merge
 * (o passo do CI trata != 2xx como reprovação). É o "teste de regressão" da IA.
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    private final QualityGateService gate;

    public QualityController(QualityGateService gate) {
        this.gate = gate;
    }

    @PostMapping("/{componentId}/gate")
    public ResponseEntity<QualityGate.Report> runGate(@PathVariable String componentId) {
        QualityGate.Report report = gate.run(componentId);
        return report.gatePassed()
                ? ResponseEntity.ok(report)
                : ResponseEntity.unprocessableEntity().body(report);
    }
}
