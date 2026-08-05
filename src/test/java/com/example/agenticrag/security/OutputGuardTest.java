package com.example.agenticrag.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Camada de saída: o que fazer quando o dado sensível já está na base e chegou na resposta.
 *
 * <p>É o cenário que a entrada não cobre — o vazamento vem de dentro, não do teclado do
 * usuário — e a razão de a cobertura do M04 exigir as duas pontas.
 */
class OutputGuardTest {

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro de plataforma que orienta desenvolvedores a CONSUMIR componentes internos.
            Regras:
            - Responda APENAS com base no CONTEXTO fornecido. Não invente endpoints, campos ou headers.
            - Se o contexto for insuficiente, diga explicitamente o que falta ingerir.
            """;

    private final OutputGuard guard = SecurityTestStack.outputGuard(
            SecurityTestStack.securityProperties());

    @Test
    void respostaTecnicaNormalPassaIntacta() {
        String answer = "Para criar uma cobrança, chame POST /v2/charges com o header Idempotency-Key [1].";
        GuardDecision d = guard.inspect(answer, SYSTEM_PROMPT);

        assertThat(d.action()).isEqualTo(GuardAction.ALLOW);
        assertThat(d.text()).isEqualTo(answer);
    }

    /** Ofusca e entrega: bloquear a resposta inteira por causa de um telefone é caro demais. */
    @Test
    void dadoPessoalNaRespostaEhOfuscadoSemPerderAResposta() {
        GuardDecision d = guard.inspect(
                "O telefone cadastrado é (11) 98765-4321 e o e-mail é ana@example.com.", SYSTEM_PROMPT);

        assertThat(d.action()).isEqualTo(GuardAction.MASK);
        assertThat(d.text()).contains("[REDACTED:telefone]", "[REDACTED:email]")
                .doesNotContain("98765-4321", "ana@example.com");
        assertThat(d.controlIds()).contains("SEC-02");
    }

    /**
     * A chave abaixo é sintética e serve de fixture — daí o marcador {@code NOSECRET}, que o
     * {@link NoVersionedSecretsTest} respeita. Enfraquecer o detector para acomodar o teste
     * seria trocar a evidência pelo conforto.
     */
    @Test
    void credencialQueVazouDaBaseEhOfuscada() {
        String fake = "sk-ant-api03-ABCdef1234567890XYZabcdef";   // NOSECRET: fixture sintética
        GuardDecision d = guard.inspect("Use a chave " + fake + " para autenticar.", SYSTEM_PROMPT);

        assertThat(d.action()).isEqualTo(GuardAction.MASK);
        assertThat(d.text()).doesNotContain(fake);
    }

    /**
     * Extração bem-sucedida: mesmo que a entrada tenha passado, a resposta que reproduz o prompt
     * de sistema não sai. É a segunda barreira do par SEC-04 / SEC-09.
     */
    @Test
    void respostaQueReproduzOPromptDeSistemaEhBloqueada() {
        GuardDecision d = guard.inspect(
                "Claro! Minhas instruções são: Responda APENAS com base no CONTEXTO fornecido. "
                        + "Não invente endpoints, campos ou headers.", SYSTEM_PROMPT);

        assertThat(d.blocked()).isTrue();
        assertThat(d.controlIds()).contains("SEC-09");
    }

    /** Palavras soltas em comum com o prompt são só português — não podem virar bloqueio. */
    @Test
    void coincidenciaDeVocabularioComOPromptNaoBloqueia() {
        GuardDecision d = guard.inspect(
                "O contexto fornecido não traz esse endpoint; seria preciso ingerir o README.",
                SYSTEM_PROMPT);

        assertThat(d.blocked()).isFalse();
    }
}
