package com.example.agenticrag.security.detector;

/**
 * Uma ocorrência encontrada por um detector.
 *
 * @param label rótulo do que casou ({@code cpf}, {@code jwt}, {@code role_override}...) — o que
 *              pode ser logado, ao contrário do trecho em si
 * @param start início (inclusivo) no texto original
 * @param end   fim (exclusivo) no texto original
 */
public record DetectorMatch(String label, int start, int end) {

    public DetectorMatch {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("intervalo inválido: [" + start + "," + end + ")");
        }
    }
}
