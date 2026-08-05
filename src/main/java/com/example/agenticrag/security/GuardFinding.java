package com.example.agenticrag.security;

/**
 * Um achado de um controle.
 *
 * <p><b>Não carrega o trecho casado.</b> O que o detector encontrou é exatamente o que não pode
 * circular — o CPF, a chave, o payload de injeção. Guardar isso no achado o levaria para o log,
 * para a resposta de erro e para o trace, transformando o controle em mais um vazamento. O que
 * sai daqui é: qual controle, de que categoria, que rótulo (ex.: {@code cpf}) e quantas vezes.
 *
 * @param controlId    id estável do controle (SEC-xx) — é a chave da matriz risco→controle→teste
 * @param category     a categoria de risco
 * @param action       o que este achado exige neste estágio
 * @param label        rótulo do que casou ({@code cpf}, {@code jwt}, {@code role_override}...)
 * @param occurrences  quantas ocorrências foram encontradas
 * @param detail       frase curta e segura para exibir ao usuário/auditor
 */
public record GuardFinding(
        String controlId,
        GuardCategory category,
        GuardAction action,
        String label,
        int occurrences,
        String detail) {
}
