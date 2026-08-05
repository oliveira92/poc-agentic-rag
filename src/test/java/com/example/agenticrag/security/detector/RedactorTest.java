package com.example.agenticrag.security.detector;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A ofuscação é onde um erro de índice vira vazamento parcial — meio CPF ainda é CPF.
 * Estes testes cobrem os dois modos de falha reais: deslocamento de offset ao substituir do
 * início para o fim, e sobreposição entre detectores diferentes.
 */
class RedactorTest {

    @Test
    void substituiPreservandoORestanteDoTexto() {
        String text = "Meu CPF é 529.982.247-25, obrigado.";
        String out = Redactor.redact(text, List.of(new DetectorMatch("cpf", 10, 24)));
        assertThat(out).isEqualTo("Meu CPF é [REDACTED:cpf], obrigado.");
    }

    /**
     * O trecho substituído é MAIOR que o original ("[REDACTED:cpf]" tem 14 chars). Substituir do
     * começo empurraria o segundo casamento e a segunda ofuscação cairia no lugar errado.
     */
    @Test
    void multiplosCasamentosNaoDeslocamUnsAosOutros() {
        String text = "a@b.com e c@d.com";
        String out = Redactor.redact(text, List.of(
                new DetectorMatch("email", 0, 7),
                new DetectorMatch("email", 10, 17)));
        assertThat(out).isEqualTo("[REDACTED:email] e [REDACTED:email]");
    }

    /** Dois detectores casando o mesmo trecho viram uma substituição só, não uma dentro da outra. */
    @Test
    void casamentosSobrepostosSaoUnidos() {
        String text = "numero 52998224725 aqui";
        String out = Redactor.redact(text, List.of(
                new DetectorMatch("cpf", 7, 18),
                new DetectorMatch("telefone", 7, 18)));
        assertThat(out).isEqualTo("numero [REDACTED:cpf] aqui");
    }

    @Test
    void casamentosAdjacentesSaoUnidos() {
        assertThat(Redactor.merge(List.of(
                new DetectorMatch("a", 0, 5),
                new DetectorMatch("b", 5, 9)), 20)).hasSize(1);
    }

    @Test
    void intervaloForaDoTextoEhDescartado() {
        assertThat(Redactor.redact("curto", List.of(new DetectorMatch("x", 10, 20))))
                .isEqualTo("curto");
    }

    @Test
    void semCasamentoDevolveOTextoIntacto() {
        assertThat(Redactor.redact("nada aqui", List.of())).isEqualTo("nada aqui");
    }
}
