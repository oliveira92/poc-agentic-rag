package com.example.agenticrag.security.detector;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O contrato que sustenta a ofuscação: normalizar <b>sem</b> mudar o comprimento.
 *
 * <p>Se a normalização deslocasse índices, todo offset devolvido por um detector que casa sobre
 * o texto dobrado apontaria para o lugar errado no texto original — e a ofuscação cortaria a
 * palavra ao lado em vez do dado.
 */
class TextFoldTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Idempotência",
            "instruções anteriores",
            "AÇÃO ÚNICA às três",
            "sem acento nenhum",
            "",
    })
    void comprimentoEhPreservado(String input) {
        assertThat(TextFold.fold(input)).hasSameSizeAs(input);
    }

    @Test
    void removeAcentoEBaixaCaixa() {
        assertThat(TextFold.fold("Idempotência")).isEqualTo("idempotencia");
        assertThat(TextFold.fold("INSTRUÇÕES")).isEqualTo("instrucoes");
    }

    @Test
    void nuloViraStringVazia() {
        assertThat(TextFold.fold(null)).isEmpty();
    }
}
