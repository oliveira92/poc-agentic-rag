package com.example.agenticrag.security;

import java.util.List;
import java.util.stream.Stream;

/**
 * O que a camada de segurança fez nesta chamada, no formato que sai na API.
 *
 * <p>Está no {@code AdviceResult} de propósito: um controle que age em silêncio vira suporte
 * ("por que a resposta está estranha?") e vira desconfiança. Dizer "mascaramos um CPF pelo
 * SEC-02" explica a resposta sem repetir o dado que foi mascarado.
 *
 * @param inputAction  o que aconteceu com a pergunta
 * @param outputAction o que aconteceu com a resposta
 * @param controls     ids dos controles que dispararam (entrada + saída)
 * @param categories   categorias de risco envolvidas
 */
public record SecurityVerdict(
        GuardAction inputAction,
        GuardAction outputAction,
        List<String> controls,
        List<String> categories) {

    public static SecurityVerdict of(GuardDecision input, GuardDecision output) {
        List<String> controls = Stream
                .concat(input.controlIds().stream(), output.controlIds().stream())
                .distinct().toList();
        List<String> categories = Stream
                .concat(input.categories().stream(), output.categories().stream())
                .distinct().map(Enum::name).toList();
        return new SecurityVerdict(input.action(), output.action(), controls, categories);
    }

    /** {@code true} quando algum controle agiu — o que a UI usa para exibir o aviso. */
    public boolean acted() {
        return inputAction != GuardAction.ALLOW || outputAction != GuardAction.ALLOW;
    }
}
