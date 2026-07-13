package com.example.agenticrag.ingestion;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunking consciente de estrutura para README markdown.
 *
 * <p>Estratégia: quebra por seções de nível {@code ##}/{@code ###} (preserva contexto
 * semântico) e sub-quebra seções longas por parágrafo até um teto de caracteres.
 * Isso é melhor que um split cego por tamanho: mantém título+conteúdo juntos, o que
 * melhora a qualidade do embedding e das citações.
 */
@Component
public class MarkdownChunker {

    private static final int MAX_CHARS = 1200;

    /** Um trecho indexável com o título da seção de origem (vira metadado/citação). */
    public record Chunk(String section, String text) {
    }

    public List<Chunk> chunk(String markdown) {
        List<Chunk> chunks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return chunks;
        }

        String currentSection = "Introdução";
        StringBuilder buffer = new StringBuilder();

        for (String line : markdown.lines().toList()) {
            if (line.startsWith("## ") || line.startsWith("### ")) {
                flush(chunks, currentSection, buffer);
                currentSection = line.replaceFirst("^#+\\s*", "").trim();
            } else {
                if (buffer.length() + line.length() > MAX_CHARS) {
                    flush(chunks, currentSection, buffer);
                }
                buffer.append(line).append('\n');
            }
        }
        flush(chunks, currentSection, buffer);
        return chunks;
    }

    private void flush(List<Chunk> chunks, String section, StringBuilder buffer) {
        String text = buffer.toString().strip();
        if (!text.isEmpty()) {
            chunks.add(new Chunk(section, text));
        }
        buffer.setLength(0);
    }
}
