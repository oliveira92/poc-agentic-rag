package com.example.agenticrag.security;

import java.util.Set;

/**
 * Quem está perguntando e o que essa pessoa pode ver.
 *
 * <p>Existe para o controle que <b>não</b> é filtro de texto: "Qual a situação do sinistro
 * SIN-8802?" é uma frase perfeitamente educada, sem PII, sem injeção, sem URL — e é abuso se
 * o sinistro não for de quem perguntou. Nenhum regex e nenhum juiz LLM resolvem isso, porque a
 * informação que falta não está no texto: está em quem é o requisitante. É autorização
 * (BOLA/IDOR na linguagem de API), e o dado tem de vir de fora.
 *
 * <p>{@link #anonymous()} representa "não há identidade propagada" — o estado atual do
 * {@code /advise} desta PoC, em que o controle fica <b>inerte</b> por falta de insumo. Está
 * registrado como lacuna e risco residual, não como controle implementado.
 *
 * @param subjectId       identificador do requisitante (nulo quando anônimo)
 * @param ownedResources  ids que este requisitante pode consultar
 */
public record SecuritySubject(String subjectId, Set<String> ownedResources) {

    private static final SecuritySubject ANONYMOUS = new SecuritySubject(null, Set.of());

    public SecuritySubject {
        ownedResources = ownedResources == null ? Set.of() : Set.copyOf(ownedResources);
    }

    public static SecuritySubject anonymous() {
        return ANONYMOUS;
    }

    /** Sem identidade não há o que autorizar — o controle se declara inerte em vez de fingir. */
    public boolean identified() {
        return subjectId != null && !subjectId.isBlank();
    }

    public boolean owns(String resourceId) {
        return ownedResources.contains(resourceId);
    }
}
