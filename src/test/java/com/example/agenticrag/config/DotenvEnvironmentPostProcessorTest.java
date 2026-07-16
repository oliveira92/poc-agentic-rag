package com.example.agenticrag.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Teste unitário do parsing do .env e da computação do header Basic (sem Spring). */
class DotenvEnvironmentPostProcessorTest {

    @Test
    void parseIgnoraComentariosERemoveAspas(@TempDir Path dir) throws IOException {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                # comentário deve ser ignorado
                ANTHROPIC_API_KEY=sk-ant-abc

                LANGFUSE_BASE_URL="http://localhost:3000"
                LANGFUSE_PUBLIC_KEY=pk-lf-1
                """);

        Map<String, Object> map = DotenvEnvironmentPostProcessor.parseDotenv(env);

        assertThat(map)
                .containsEntry("ANTHROPIC_API_KEY", "sk-ant-abc")
                .containsEntry("LANGFUSE_BASE_URL", "http://localhost:3000")   // aspas removidas
                .containsEntry("LANGFUSE_PUBLIC_KEY", "pk-lf-1")
                .hasSize(3);
    }

    @Test
    void arquivoInexistenteRetornaVazio(@TempDir Path dir) {
        assertThat(DotenvEnvironmentPostProcessor.parseDotenv(dir.resolve("nao-existe.env"))).isEmpty();
    }

    @Test
    void basicAuthComputaBase64() {
        String esperado = "Basic " + Base64.getEncoder()
                .encodeToString("pk-lf-test:sk-lf-test".getBytes(StandardCharsets.UTF_8));
        assertThat(DotenvEnvironmentPostProcessor.basicAuth("pk-lf-test", "sk-lf-test")).isEqualTo(esperado);
    }

    @Test
    void basicAuthNuloQuandoFaltaChave() {
        assertThat(DotenvEnvironmentPostProcessor.basicAuth(null, "sk")).isNull();
        assertThat(DotenvEnvironmentPostProcessor.basicAuth("pk", " ")).isNull();
        assertThat(DotenvEnvironmentPostProcessor.basicAuth("", "sk")).isNull();
    }
}
