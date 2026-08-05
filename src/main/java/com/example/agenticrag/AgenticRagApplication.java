package com.example.agenticrag;

import com.example.agenticrag.model.ModelsProperties;
import com.example.agenticrag.embedding.EmbeddingProperties;
import com.example.agenticrag.observability.CostProperties;
import com.example.agenticrag.routing.RoutingProperties;
import com.example.agenticrag.security.SecurityProperties;
import com.example.agenticrag.security.dataset.SecurityDatasetProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

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
// EmbeddingProperties é registrada AQUI, e não na LocalOnnxEmbeddingConfig, porque o
// 'model-id' que ela carrega é consumido pela ingestão nos DOIS backends de embedding — com
// modelo local e com gateway. Registrá-la junto da config do ONNX a fazia desaparecer quando
// APP_EMBEDDING_PROVIDER=openai, derrubando o contexto.
@EnableConfigurationProperties({RoutingProperties.class, ModelsProperties.class, CostProperties.class,
        SecurityProperties.class, SecurityDatasetProperties.class, EmbeddingProperties.class})
public class AgenticRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgenticRagApplication.class, args);
    }
}
