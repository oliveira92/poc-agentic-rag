package com.example.agenticrag.quality;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A03 — juiz determinístico (mock): fundamentação alta quando a resposta usa termos do contexto. */
class MockGroundednessEvaluatorTest {

    private final MockGroundednessEvaluator judge = new MockGroundednessEvaluator();

    @Test
    void groundedAnswerScoresHigh() {
        String context = "O endpoint refund estorna cobranças. Idempotência exige header idempotency.";
        String answer = "Use refund para estornar cobranças, tratando idempotência com header idempotency.";
        GroundednessResult r = judge.evaluate("como estornar?", context, answer);
        assertThat(r.score()).isGreaterThanOrEqualTo(4);
        assertThat(r.judge()).isEqualTo("mock");
    }

    @Test
    void answerWithoutContextIsUngrounded() {
        GroundednessResult r = judge.evaluate("q", "", "Qualquer resposta inventada aqui.");
        assertThat(r.score()).isEqualTo(1);
        assertThat(r.normalized()).isEqualTo(0.2);
    }

    @Test
    void fabricatedAnswerScoresLow() {
        String context = "O componente cria e estorna cobranças.";
        String answer = "Envie notificações WhatsApp configurando webhooks Telegram Instagram Facebook.";
        GroundednessResult r = judge.evaluate("q", context, answer);
        assertThat(r.score()).isLessThanOrEqualTo(2);
    }
}
