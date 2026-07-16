package com.example.agenticrag;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Ponto de entrada da PoC Agentic RAG - Component Advisor.
 *
 * <p>Fase 1: Structured RAG. O agente ingere um componente do portal (API + README),
 * indexa no pgvector (memória de longo prazo), responde "como consumir" com citações,
 * e permite aprovar a resposta da LLM para realimentar a base de conhecimento (HITL).
 */
@SpringBootApplication
public class AgenticRagApplication {

    public static void main(String[] args) {
        configureLangfuseAuth();
        SpringApplication.run(AgenticRagApplication.class, args);
    }

    /**
     * Conexão com o Langfuse pelo padrão de variáveis do SDK: {@code LANGFUSE_PUBLIC_KEY},
     * {@code LANGFUSE_SECRET_KEY} e {@code LANGFUSE_BASE_URL}.
     *
     * <p>O endpoint OTLP é montado a partir de {@code LANGFUSE_BASE_URL} no application.yml.
     * Aqui computamos o header {@code Authorization: Basic base64(publicKey:secretKey)} —
     * algo que placeholders do Spring não fazem — e o injetamos como propriedade do tracing
     * antes de o contexto subir. Sem as chaves, nenhum header é enviado (traces só não são aceitos).
     *
     * <p>Boot 4: a propriedade é {@code management.opentelemetry.tracing.export.otlp.headers.*}
     * (no Boot 3 era {@code management.otlp.tracing.headers.*}).
     */
    private static void configureLangfuseAuth() {
        String publicKey = System.getenv("LANGFUSE_PUBLIC_KEY");
        String secretKey = System.getenv("LANGFUSE_SECRET_KEY");
        if (publicKey != null && !publicKey.isBlank() && secretKey != null && !secretKey.isBlank()) {
            String basic = Base64.getEncoder()
                    .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
            System.setProperty(
                    "management.opentelemetry.tracing.export.otlp.headers.Authorization", "Basic " + basic);
        }
    }
}
