package com.example.agenticrag.web;

import com.example.agenticrag.observability.RagMetrics;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Camada 4 (Negócio): captura do CSAT — o cliente clicou 👍/👎 na resposta.
 *
 * <p>Mesma mecânica do score de qualidade (A03), mas agora quem pontua é o usuário: o
 * feedback vira um SLI de satisfação por rota ({@code rag.csat}) e um evento no trace,
 * ligando a experiência real à chamada técnica (A04). O cliente reenvia {@code route} e
 * {@code traceId} recebidos no {@code /advise} para correlacionar (A01).
 */
@RestController
@RequestMapping("/api/v1/components/{componentId}")
public class FeedbackController {

    private final RagMetrics metrics;
    private final ObjectProvider<Tracer> tracerProvider;

    public FeedbackController(RagMetrics metrics, ObjectProvider<Tracer> tracerProvider) {
        this.metrics = metrics;
        this.tracerProvider = tracerProvider;
    }

    /**
     * @param value 1 = positivo (👍), 0 = negativo (👎)
     * @param route rota devolvida no /advise (opcional; default "unknown")
     */
    public record FeedbackRequest(@Min(0) @Max(1) int value, String route, String traceId, String comment) {
    }

    @PostMapping("/feedback")
    public Map<String, Object> feedback(@PathVariable String componentId,
                                        @RequestBody FeedbackRequest req) {
        String route = req.route() == null || req.route().isBlank() ? "unknown" : req.route();
        metrics.recordCsat(route, req.value());

        Tracer tracer = tracerProvider.getIfAvailable();
        Span span = tracer == null ? null : tracer.currentSpan();
        if (span != null) {
            span.event(req.value() == 1 ? "user_feedback.positive" : "user_feedback.negative");
            span.tag("user_feedback", String.valueOf(req.value()));
        }
        return Map.of("componentId", componentId, "route", route, "recorded", true);
    }
}
