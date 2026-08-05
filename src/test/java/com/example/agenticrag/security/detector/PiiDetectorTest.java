package com.example.agenticrag.security.detector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O que se testa aqui não é "acha CPF" — é que <b>não</b> acha CPF onde não tem.
 *
 * <p>Detector de PII que dispara em número de protocolo é desligado na primeira semana de
 * produção, e um controle desligado protege zero. Por isso metade destes casos é de coisas
 * que devem passar limpo.
 */
class PiiDetectorTest {

    private final PiiDetector detector = new PiiDetector();

    private List<String> labels(String text) {
        return detector.find(text).stream().map(DetectorMatch::label).toList();
    }

    @Test
    void cpfValidoComPontuacaoEhDetectado() {
        assertThat(labels("Meu CPF é 529.982.247-25, confere?")).contains("cpf");
    }

    @Test
    void cpfValidoSemPontuacaoEhDetectado() {
        assertThat(labels("cpf 52998224725")).contains("cpf");
    }

    /** Regressão: o ponto final da frase encerrava o casamento e o CPF passava reto. */
    @Test
    void cpfNoFimDaFraseEhDetectado() {
        assertThat(labels("Cliente de teste: CPF 529.982.247-25.")).contains("cpf");
    }

    @Test
    void cpfNoMeioDeUmNumeroMaiorNaoEhDetectado() {
        assertThat(labels("valor 12.529.982.247-25")).doesNotContain("cpf");
    }

    /** 11 dígitos com dígito verificador errado: é protocolo, não CPF. */
    @Test
    void numeroDeOnzeDigitosInvalidoNaoEhCpf() {
        assertThat(labels("protocolo 12345678901")).doesNotContain("cpf");
    }

    @Test
    void sequenciaRepetidaNaoEhCpf() {
        assertThat(labels("111.111.111-11")).doesNotContain("cpf");
    }

    @Test
    void celularComDddEhDetectado() {
        assertThat(labels("meu contato é (11) 98765-4321")).contains("telefone");
    }

    /** DDD 01 não existe — número de protocolo com 10 dígitos não pode virar telefone. */
    @Test
    void numeroComDddInexistenteNaoEhTelefone() {
        assertThat(labels("chamado 0123456789")).doesNotContain("telefone");
    }

    @Test
    void emailEhDetectado() {
        assertThat(labels("escreva para joao.silva@example.com")).contains("email");
    }

    @Test
    void cartaoValidoPeloLuhnEhDetectado() {
        assertThat(labels("cartão 4111 1111 1111 1111")).contains("cartao");
    }

    @Test
    void numeroLongoQueFalhaNoLuhnNaoEhCartao() {
        assertThat(labels("id 4111111111111112")).doesNotContain("cartao");
    }

    /** Um id de endpoint da própria base não pode ser confundido com dado pessoal. */
    @Test
    void textoTecnicoDoDominioPassaLimpo() {
        assertThat(detector.find("POST /v2/charges exige Idempotency-Key (UUID). Limite 100 req/s."))
                .isEmpty();
    }
}
