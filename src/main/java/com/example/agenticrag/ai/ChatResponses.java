package com.example.agenticrag.ai;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

/**
 * Extração segura do texto de resposta com a família Claude 5 (thinking adaptativo).
 *
 * <p>O Spring AI 2.0 mapeia cada bloco de <i>thinking</i> como uma {@link Generation}
 * separada, adicionada ANTES da geração de texto (ver {@code AnthropicChatModel.buildGenerations});
 * e {@code ChatResponse.getResult()} devolve a PRIMEIRA. Resultado: em perguntas complexas
 * (quando o modelo "pensa"), {@code getResult().getOutput().getText()} devolvia o bloco de
 * raciocínio — e a resposta chegava vazia (bug intermitente diagnosticado ao vivo: 46s,
 * output=4276 tokens e answer com 0 chars).
 *
 * <p>A geração de TEXTO é sempre a última — é dela que se extrai a resposta.
 */
public final class ChatResponses {

    private ChatResponses() {
    }

    /** Texto final da resposta (última Generation); nunca nulo. */
    public static String answerText(ChatResponse response) {
        if (response == null) {
            return "";
        }
        List<Generation> generations = response.getResults();
        if (generations == null || generations.isEmpty()) {
            return "";
        }
        String text = generations.get(generations.size() - 1).getOutput().getText();
        return text == null ? "" : text;
    }
}
