package com.example.agenticrag.ingestion;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Teste unitário (sem Spring) do chunking consciente de estrutura. */
class MarkdownChunkerTest {

    private final MarkdownChunker chunker = new MarkdownChunker();

    @Test
    void quebraPorSecoes() {
        String md = """
                # Título

                ## Autenticação
                Use Bearer token.

                ## Idempotência
                Envie Idempotency-Key.
                """;

        List<MarkdownChunker.Chunk> chunks = chunker.chunk(md);

        assertThat(chunks).extracting(MarkdownChunker.Chunk::section)
                .contains("Autenticação", "Idempotência");
        assertThat(chunks).anyMatch(c -> c.text().contains("Bearer token"));
    }

    @Test
    void entradaVaziaRetornaListaVazia() {
        assertThat(chunker.chunk(null)).isEmpty();
        assertThat(chunker.chunk("   ")).isEmpty();
    }
}
