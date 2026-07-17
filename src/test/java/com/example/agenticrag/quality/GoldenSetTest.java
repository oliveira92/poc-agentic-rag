package com.example.agenticrag.quality;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** A05 — golden set versionado no classpath; parser tolera vírgulas dentro de aspas. */
class GoldenSetTest {

    private final GoldenSet goldenSet = new GoldenSet();

    @Test
    void csvParserHandlesQuotedCommas() {
        List<String> fields = GoldenSet.parseCsvLine("g1,c,\"cria cobrança, com idempotência?\",ref,HIGH,facil");
        assertThat(fields).containsExactly("g1", "c", "cria cobrança, com idempotência?", "ref", "HIGH", "facil");
    }

    @Test
    void loadsPaymentsSdkGoldenSet() {
        List<GoldenCase> cases = goldenSet.load("payments-sdk");
        assertThat(cases).isNotEmpty();
        assertThat(cases).anyMatch(GoldenCase::isTrap);              // tem armadilhas
        assertThat(cases).anyMatch(c -> !c.isTrap());               // tem casos reais
        assertThat(cases).anyMatch(c -> c.kind().equals("facil"));
    }
}
