package com.example.agenticrag.config;

import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Carrega o arquivo {@code .env} (se existir) para o Environment do Spring, de modo que as
 * variáveis (ANTHROPIC_API_KEY, LANGFUSE_*, DB_*, ...) fiquem disponíveis <b>sem precisar dar
 * {@code source} no shell</b> — o Spring Boot não lê {@code .env} por padrão.
 *
 * <p>Também computa o header {@code Authorization: Basic base64(publicKey:secretKey)} do Langfuse
 * a partir de {@code LANGFUSE_PUBLIC_KEY}/{@code LANGFUSE_SECRET_KEY} — algo que placeholders do
 * Spring não fazem — e o injeta como a propriedade OTLP de tracing do Boot 4.
 *
 * <p>Precedência: variáveis reais do ambiente e {@code -D} <b>vencem</b> o {@code .env}
 * (é apenas um fallback de desenvolvimento). Registrado em {@code META-INF/spring.factories}.
 * O caminho do arquivo pode ser trocado via {@code -Ddotenv.path}.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String OTLP_AUTH_PROPERTY =
            "management.opentelemetry.tracing.export.otlp.headers.Authorization";

    private final Log log;

    public DotenvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path path = Path.of(System.getProperty("dotenv.path", ".env"));
        Map<String, Object> dotenv = parseDotenv(path);
        if (!dotenv.isEmpty()) {
            // addLast = menor precedência: env vars reais e -D continuam vencendo.
            environment.getPropertySources().addLast(new MapPropertySource("dotenv", dotenv));
            log.info("Carregado .env (" + dotenv.size() + " variáveis) como fallback local.");
        }

        String header = basicAuth(
                environment.getProperty("LANGFUSE_PUBLIC_KEY"),
                environment.getProperty("LANGFUSE_SECRET_KEY"));
        if (header != null) {
            environment.getPropertySources().addFirst(
                    new MapPropertySource("langfuseOtlpAuth", Map.of(OTLP_AUTH_PROPERTY, header)));
            log.info("Header OTLP do Langfuse configurado (Basic) a partir de LANGFUSE_PUBLIC_KEY/SECRET_KEY.");
        }
    }

    /** Lê o .env (KEY=VALUE), ignorando comentários/linhas em branco e removendo aspas. */
    static Map<String, Object> parseDotenv(Path path) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (!Files.isRegularFile(path)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String s = line.strip();
                if (s.isEmpty() || s.startsWith("#")) {
                    continue;
                }
                int eq = s.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                map.put(s.substring(0, eq).strip(), stripQuotes(s.substring(eq + 1).strip()));
            }
        } catch (Exception e) {
            // .env é opcional; não falhar o boot por causa dele.
            System.err.println("Aviso: falha ao ler .env: " + e.getMessage());
        }
        return map;
    }

    /** {@code "Basic base64(pk:sk)"}, ou {@code null} se qualquer chave estiver ausente. */
    static String basicAuth(String publicKey, String secretKey) {
        if (publicKey == null || publicKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            return null;
        }
        String token = Base64.getEncoder()
                .encodeToString((publicKey + ":" + secretKey).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private static String stripQuotes(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
