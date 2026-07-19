package com.example.agenticrag.ai;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Claude 5 + thinking (bug real diagnosticado): o Spring AI 2.0 põe o bloco de raciocínio
 * como a PRIMEIRA Generation e o texto final como a ÚLTIMA — getResult() (primeira) devolvia
 * o raciocínio/vazio. answerText() extrai sempre da última.
 */
class ChatResponsesTest {

    private static Generation gen(String text, Map<String, Object> props) {
        return new Generation(AssistantMessage.builder().content(text).properties(props).build());
    }

    @Test
    void singleTextGenerationReturnsIt() {
        ChatResponse r = new ChatResponse(List.of(gen("resposta direta", Map.of())));
        assertThat(ChatResponses.answerText(r)).isEqualTo("resposta direta");
    }

    @Test
    void thinkingFirstTextLastReturnsText() {
        ChatResponse r = new ChatResponse(List.of(
                gen("raciocínio interno do modelo...", Map.of("signature", "abc")), // thinking
                gen("a resposta de verdade", Map.of())));
        assertThat(ChatResponses.answerText(r)).isEqualTo("a resposta de verdade");
    }

    @Test
    void emptyResponseYieldsEmptyString() {
        assertThat(ChatResponses.answerText(null)).isEmpty();
        assertThat(ChatResponses.answerText(new ChatResponse(List.of()))).isEmpty();
    }
}
