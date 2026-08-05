package com.example.agenticrag.security;

/**
 * O que fazer com um achado. A ordem das constantes é a <b>severidade</b>: quando vários
 * controles disparam, vence o de maior ordinal ({@link #max}).
 *
 * <p>{@link #MASK} existir é uma decisão de projeto, não um meio-termo preguiçoso: bloquear
 * toda pergunta com CPF derrubaria o caso legítimo do titular conferindo o próprio cadastro
 * (L-05 do dataset). Mascarar atende o requisito "não enviar dados sensíveis ao modelo" e
 * ainda responde a pessoa.
 */
public enum GuardAction {

    /** Nada a fazer — segue o fluxo. */
    ALLOW,

    /** Ofusca os trechos casados e segue com o texto sanitizado. */
    MASK,

    /** Interrompe: a requisição não chega ao modelo (ou a resposta não chega ao usuário). */
    BLOCK;

    public static GuardAction max(GuardAction a, GuardAction b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
