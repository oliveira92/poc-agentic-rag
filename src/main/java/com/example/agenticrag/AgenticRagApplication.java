package com.example.agenticrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da PoC Agentic RAG - Component Advisor.
 *
 * <p>Fase 1: Structured RAG. O agente ingere um componente do portal (API + README),
 * indexa no pgvector (memória de longo prazo), responde "como consumir" com citações,
 * e permite aprovar a resposta da LLM para realimentar a base de conhecimento (HITL).
 *
 * <p>O carregamento do {@code .env} e o header OTLP do Langfuse são resolvidos por
 * {@link com.example.agenticrag.config.DotenvEnvironmentPostProcessor} (antes do contexto subir).
 */
@SpringBootApplication
public class AgenticRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticRagApplication.class, args);
    }
}
