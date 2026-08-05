package com.example.agenticrag.security.dataset;

/**
 * Um caso do dataset.
 *
 * @param id       identificador estável (L-01, A-04, F-03) — é a chave da evidência
 * @param type     {@code legitimo} | {@code abuso} | {@code fora_escopo}
 * @param question o texto exato enviado ao sistema
 */
public record SecurityCase(String id, String type, String question) {

    private static final String LEGIT = "legitimo";

    /** Casos que o sistema DEVE barrar (abuso e fora de escopo). */
    public boolean shouldBlock() {
        return !LEGIT.equalsIgnoreCase(type);
    }

    public boolean legitimate() {
        return LEGIT.equalsIgnoreCase(type);
    }
}
