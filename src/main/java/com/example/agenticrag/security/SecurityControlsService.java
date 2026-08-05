package com.example.agenticrag.security;

import com.example.agenticrag.security.detector.Detector;
import com.example.agenticrag.security.detector.ResourceOwnershipDetector;
import com.example.agenticrag.security.detector.SystemPromptLeakDetector;
import com.example.agenticrag.security.scope.ScopeJudge;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Inventário dos controles ativos, montado a partir dos <b>beans reais</b> e da política em
 * config — não de uma lista escrita à mão.
 *
 * <p>Um inventário mantido manualmente descola do código na primeira semana, e aí a matriz de
 * evidências passa a documentar um sistema que não existe mais. Aqui, apagar um detector some
 * com a linha; mudar MASK para BLOCK no YAML muda a coluna. É o que faz
 * {@code GET /api/v1/security/controls} valer como evidência em vez de como intenção.
 */
@Service
public class SecurityControlsService {

    /**
     * Um controle e o que ele faz em cada estágio.
     *
     * @param id             SEC-xx
     * @param category       categoria de risco
     * @param description    o que procura
     * @param implementation onde está implementado (classe) — o ponteiro para o revisor
     * @param actions        ação por estágio, só onde a categoria é tratada
     */
    public record ControlInfo(String id, String category, String description,
                              String implementation, Map<String, GuardAction> actions) {
    }

    private final List<Detector> detectors;
    private final ResourceOwnershipDetector ownership;
    private final SystemPromptLeakDetector leak;
    private final ScopeJudge scopeJudge;
    private final SecurityProperties props;

    public SecurityControlsService(List<Detector> detectors,
                                   ResourceOwnershipDetector ownership,
                                   SystemPromptLeakDetector leak,
                                   ScopeJudge scopeJudge,
                                   SecurityProperties props) {
        this.detectors = detectors;
        this.ownership = ownership;
        this.leak = leak;
        this.scopeJudge = scopeJudge;
        this.props = props;
    }

    /** Estado geral da camada — o cabeçalho da evidência. */
    public Map<String, Object> status() {
        return Map.of(
                "enabled", props.enabled(),
                "failMode", props.failClosed() ? "closed" : "open",
                "scopeJudge", scopeJudge.name(),
                "maxQuestionChars", props.maxQuestionChars(),
                "maxDocumentChars", props.maxDocumentChars(),
                "controls", list().size());
    }

    public List<ControlInfo> list() {
        List<ControlInfo> out = new ArrayList<>();
        for (Detector d : detectors) {
            out.add(info(d.controlId(), d.category(), d.description(), d.getClass()));
        }
        out.add(info(ownership.controlId(), ownership.category(), ownership.description(),
                ownership.getClass()));
        out.add(info(leak.controlId(), leak.category(), leak.description(), leak.getClass()));
        out.add(info("SEC-07", GuardCategory.OUT_OF_SCOPE,
                "Pergunta ou documento fora do conteúdo ingerido (juiz: " + scopeJudge.name() + ").",
                scopeJudge.getClass()));
        out.add(info("SEC-08", GuardCategory.OVERSIZE,
                "Texto acima do teto configurado — prompt stuffing e abuso de recurso.",
                InputGuard.class));
        out.sort(Comparator.comparing(ControlInfo::id));
        return out;
    }

    private ControlInfo info(String id, GuardCategory category, String description, Class<?> impl) {
        Map<String, GuardAction> actions = new LinkedHashMap<>();
        for (GuardStage stage : GuardStage.values()) {
            GuardAction action = props.actionFor(stage, category);
            if (action != GuardAction.ALLOW) {
                actions.put(stage.name(), action);
            }
        }
        return new ControlInfo(id, category.name(), description, impl.getSimpleName(), actions);
    }
}
