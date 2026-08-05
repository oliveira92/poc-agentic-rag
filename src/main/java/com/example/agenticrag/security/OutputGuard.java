package com.example.agenticrag.security;

import com.example.agenticrag.security.detector.DetectorMatch;
import com.example.agenticrag.security.detector.PiiDetector;
import com.example.agenticrag.security.detector.SecretDetector;
import com.example.agenticrag.security.detector.SystemPromptLeakDetector;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda de <b>saída</b>: a última chance antes de o texto chegar ao usuário — e, nesta PoC,
 * antes de virar rascunho para aprovação humana e potencialmente ser reindexado.
 *
 * <p>Por que existe, se a entrada e a ingestão já filtram: porque as duas podem falhar e o dado
 * sensível ter chegado à base por um caminho que ninguém previu (um README atualizado, um
 * conhecimento aprovado no HITL, um documento legado). O controle de saída é o que transforma
 * "confiamos que nada sensível entrou" em "nada sensível sai", e é a diferença entre um
 * vazamento e um incidente evitado.
 *
 * <p>Aqui a ação padrão para PII é MASK, não BLOCK: bloquear a resposta inteira porque ela
 * continha um telefone destrói a utilidade do agente no caso legítimo (L-06 do dataset — o
 * titular pedindo o telefone que a empresa tem registrado dele). Mascarar entrega a resposta
 * sem entregar o dado.
 */
@Component
public class OutputGuard {

    private final GuardEngine engine;
    private final PiiDetector pii;
    private final SecretDetector secret;
    private final SystemPromptLeakDetector leak;

    public OutputGuard(GuardEngine engine, PiiDetector pii, SecretDetector secret,
                       SystemPromptLeakDetector leak) {
        this.engine = engine;
        this.pii = pii;
        this.secret = secret;
        this.leak = leak;
    }

    /**
     * @param answer       resposta gerada
     * @param systemPrompt prompt de sistema da chamada (insumo do detector de vazamento)
     */
    public GuardDecision inspect(String answer, String systemPrompt) {
        if (!engine.enabled() || answer == null || answer.isBlank()) {
            return GuardDecision.allow(GuardStage.OUTPUT, answer);
        }
        List<GuardEngine.RawFinding> raw = new ArrayList<>(
                engine.scan(answer, List.of(pii, secret)));

        List<DetectorMatch> leaked = leak.find(answer, systemPrompt);
        if (!leaked.isEmpty()) {
            raw.add(new GuardEngine.RawFinding(leak.controlId(), leak.category(),
                    leaked.get(0).label(), leaked, leak.description()));
        }
        return engine.decide(GuardStage.OUTPUT, answer, raw);
    }
}
