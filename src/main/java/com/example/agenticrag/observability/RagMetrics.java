package com.example.agenticrag.observability;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Camada 2 do M03 (Métricas Técnicas) + apoio à Camada 1 (Observabilidade).
 *
 * <p>Traduz cada chamada do agente em SLIs mensuráveis (Micrometer → Actuator/Prometheus →
 * pode ir para Langfuse/Grafana). Segue a tese da aula: <b>toda métrica carrega a dimensão
 * {@code model} e {@code route}</b> — "1 SLO para vários modelos é mentira". Latências
 * publicam p50/p95/p99 (percentis são robustos à cauda; a média mente).
 *
 * <p>Nomes dos meters (prefixo {@code rag.*}) para dashboards/alertas:
 * <ul>
 *   <li>{@code rag.advise.latency}{route,model,outcome} — SLI de latência do /advise (p50/p95/p99)</li>
 *   <li>{@code rag.retrieve.latency}{route} — latência só da recuperação (sem LLM)</li>
 *   <li>{@code rag.retrieve.top_score}{route} — qualidade bruta da recuperação (score do 1º doc)</li>
 *   <li>{@code rag.llm.tokens}{model,type=input|output} — tokens (custo mora aqui — A04)</li>
 *   <li>{@code rag.llm.cost}{model} — custo estimado acumulado (moeda de config)</li>
 *   <li>{@code rag.advise.ungrounded}{route} — respostas sem base recuperada (evento — A01)</li>
 *   <li>{@code rag.advise.low_confidence}{route} — guardrail anti-alucinação disparou (A03)</li>
 *   <li>{@code rag.quality.groundedness}{route} — nota do juiz (1..5) (A03)</li>
 *   <li>{@code rag.errors}{model,type} — 429/timeout/parse/other (A02)</li>
 *   <li>{@code rag.csat}{route} — feedback do usuário 👍/👎 (A04)</li>
 *   <li>{@code rag.hitl}{outcome=approved|rejected} — curadoria humana (A04/A05)</li>
 * </ul>
 */
@Component
public class RagMetrics {

    private final MeterRegistry registry;

    public RagMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Latência ponta a ponta do /advise, com percentis para o SLO por modelo/rota. */
    public void recordAdvise(String route, String model, String outcome, long nanos) {
        Timer.builder("rag.advise.latency")
                .description("Latência do /advise (recuperação + geração)")
                .tag("route", route)
                .tag("model", model)
                .tag("outcome", outcome)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /** Latência só da recuperação semântica (pgvector), sem custo de LLM. */
    public void recordRetrieve(String route, long nanos) {
        Timer.builder("rag.retrieve.latency")
                .description("Latência da recuperação semântica (pgvector)")
                .tag("route", route)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }

    /** Score de similaridade do 1º documento — SLI bruto de qualidade da recuperação. */
    public void recordTopScore(String route, double topScore) {
        DistributionSummary.builder("rag.retrieve.top_score")
                .description("Score de similaridade do documento mais relevante")
                .tag("route", route)
                .register(registry)
                .record(topScore);
    }

    /** Tokens de entrada/saída — a base do custo (A04). */
    public void recordTokens(String model, Integer inputTokens, Integer outputTokens) {
        if (inputTokens != null) {
            registry.counter("rag.llm.tokens", "model", model, "type", "input").increment(inputTokens);
        }
        if (outputTokens != null) {
            registry.counter("rag.llm.tokens", "model", model, "type", "output").increment(outputTokens);
        }
    }

    /** Custo estimado acumulado (moeda definida em app.cost.currency). */
    public void recordCost(String model, double cost) {
        registry.counter("rag.llm.cost", "model", model).increment(cost);
    }

    /** Resposta sem base recuperada (componente não ingerido) — evento de negócio (A01). */
    public void incrementUngrounded(String route) {
        registry.counter("rag.advise.ungrounded", "route", route).increment();
    }

    /** Guardrail de fidelidade disparou: o texto cita algo que não está nas fontes (A03). */
    public void incrementLowConfidence(String route) {
        registry.counter("rag.advise.low_confidence", "route", route).increment();
    }

    /** Nota de groundedness do juiz (1..5) — SLI de qualidade nº 1 do RAG (A03). */
    public void recordGroundedness(String route, int score) {
        DistributionSummary.builder("rag.quality.groundedness")
                .description("Nota de fundamentação do juiz (1..5)")
                .tag("route", route)
                .register(registry)
                .record(score);
    }

    /** Erros por tipo (429/timeout/parse/other) — cada tipo tem uma ação diferente (A02). */
    public void incrementError(String model, String type) {
        registry.counter("rag.errors", "model", model, "type", type).increment();
    }

    /** Satisfação do usuário 👍(1)/👎(0) — CSAT ligado à rota (A04). */
    public void recordCsat(String route, int value) {
        DistributionSummary.builder("rag.csat")
                .description("Feedback do usuário: 1=positivo, 0=negativo")
                .tag("route", route)
                .register(registry)
                .record(value);
    }

    /** Curadoria humana: aprovada/rejeitada — leading indicator da base (A04/A05). */
    public void recordHitl(String outcome) {
        registry.counter("rag.hitl", "outcome", outcome).increment();
    }

    /**
     * Disparo de guardrail (M04): por estágio, ação e controle.
     *
     * <p>As três dimensões juntas são o que torna a métrica acionável. Só o total de bloqueios
     * não distingue "o controle está funcionando" de "o controle está barrando usuário legítimo":
     * um pico em {@code SEC-02/MASK} na entrada é operação normal, o mesmo pico em
     * {@code SEC-02/BLOCK} na ingestão é alguém tentando envenenar a base.
     */
    public void recordGuard(String stage, String action, String controlId) {
        registry.counter("rag.guard.decisions",
                "stage", stage, "action", action, "control", controlId).increment();
    }

    /** Custo de latência da camada de segurança — o preço do controle, medido (A02). */
    public void recordGuardLatency(String stage, long nanos) {
        Timer.builder("rag.guard.latency")
                .description("Latência da avaliação de guardrails")
                .tag("stage", stage)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry)
                .record(nanos, TimeUnit.NANOSECONDS);
    }
}
