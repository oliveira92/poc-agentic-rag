package com.example.agenticrag.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Memória de CURTO prazo: janela deslizante das últimas N mensagens da conversa,
 * persistida no Postgres pelo {@link ChatMemoryRepository} (JdbcChatMemoryRepository).
 *
 * <p>Não confundir com a memória de LONGO prazo (base de conhecimento no pgvector),
 * que é recuperada por similaridade no {@code ComponentAdvisorService}.
 */
@Configuration
public class MemoryConfig {

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }
}
