package com.example.agenticrag;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base dos testes de integração. Por padrão sobe UM Postgres+pgvector via Testcontainers
 * (singleton reaproveitado entre classes) — é assim que roda na CI.
 *
 * <p>Modo alternativo p/ dev/ambientes sem Docker compatível com Testcontainers: passe
 * {@code -Dit.datasource.url=jdbc:postgresql://localhost:5432/ragdb} e os testes usam um
 * Postgres externo já em execução (ex.: {@code docker compose up -d postgres}).
 *
 * <p>Perfil {@code mock} ativa o adapter fake do portal.
 */
@SpringBootTest
@ActiveProfiles("mock")
public abstract class AbstractIntegrationTest {

    private static final String EXTERNAL_URL = System.getProperty("it.datasource.url");
    private static final boolean USE_EXTERNAL = EXTERNAL_URL != null;

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("ragdb");

    static {
        if (!USE_EXTERNAL) {
            POSTGRES.start();
        }
    }

    @DynamicPropertySource
    static void testProps(DynamicPropertyRegistry registry) {
        // Embedding pelo caminho LOCAL, não pelo gateway (que é o padrão da aplicação).
        // Teste de integração não deve depender de chave de vendor nem de rede por embedding:
        // seria lento, caro e falharia por motivo que não é o do teste. O caminho de produção
        // é exercitado subindo a app; aqui o que se verifica é ingestão, recuperação e schema.
        registry.add("spring.ai.model.embedding", () -> "transformers");

        if (USE_EXTERNAL) {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getProperty("it.datasource.username", "rag"));
            registry.add("spring.datasource.password", () -> System.getProperty("it.datasource.password", "rag"));
        } else {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        }
    }

    @Autowired
    protected JdbcClient jdbc;

    /** Isola cada teste: limpa dados sem apagar o schema (Flyway roda uma vez). */
    @BeforeEach
    void cleanState() {
        jdbc.sql("TRUNCATE vector_store, knowledge_approval, component").update();
    }
}
