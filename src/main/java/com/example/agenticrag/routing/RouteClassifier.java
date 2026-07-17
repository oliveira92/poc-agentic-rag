package com.example.agenticrag.routing;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Classifica a consulta em uma rota de negócio + nível de risco, e resolve o modelo real
 * (A03/A05). É a versão "in-process" da ideia do gateway: a escolha de modelo por rota vira
 * uma decisão central e observável, sem espalhar regra por controller.
 *
 * <p>Heurística simples e transparente (a PoC prioriza legibilidade sobre um roteador LLM):
 * termos de <b>risco</b> (dinheiro, autenticação, idempotência, erros/produção) → HIGH;
 * perguntas <b>conceituais</b> curtas → LOW; o resto → MEDIUM.
 */
@Component
public class RouteClassifier {

    private static final String[] HIGH_RISK = {
            "paga", "pagamento", "cobran", "charge", "estorn", "refund", "reembol",
            "idempot", "auth", "autentic", "token", "credencial", "seguran", "security",
            "erro", "error", "produç", "producao", "falha", "timeout", "rate limit", "webhook", "assinatura"
    };

    private static final String[] CONCEPTUAL = {
            "o que é", "o que e", "para que serve", "para que server", "visão geral",
            "visao geral", "overview", "quando usar", "conceito"
    };

    private final RoutingProperties props;

    public RouteClassifier(RoutingProperties props) {
        this.props = props;
    }

    public Route classify(String componentId, String question) {
        String q = question == null ? "" : question.toLowerCase(Locale.ROOT);

        if (containsAny(q, HIGH_RISK)) {
            return new Route("risco", RiskTier.HIGH, props.modelFor(RiskTier.HIGH));
        }
        if (containsAny(q, CONCEPTUAL) || q.split("\\s+").length <= 4) {
            return new Route("faq", RiskTier.LOW, props.modelFor(RiskTier.LOW));
        }
        return new Route("integracao", RiskTier.MEDIUM, props.modelFor(RiskTier.MEDIUM));
    }

    private static boolean containsAny(String haystack, String[] needles) {
        for (String n : needles) {
            if (haystack.contains(n)) {
                return true;
            }
        }
        return false;
    }
}
