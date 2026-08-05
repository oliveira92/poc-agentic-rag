package com.example.agenticrag.security;

import com.example.agenticrag.security.detector.DetectorMatch;
import com.example.agenticrag.security.detector.SecretDetector;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.MalformedInputException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Sem segredo versionado" vira teste, não promessa.
 *
 * <p>Varre o que está no repositório com o <b>mesmo</b> {@link SecretDetector} que protege o
 * runtime. Reusar o detector é o ponto: uma regra nova para o vazamento em produção passa a
 * valer para o commit no mesmo instante, sem uma segunda lista de padrões para manter em
 * sincronia (e, na prática, para divergir).
 *
 * <p>Isto não substitui um scanner de histórico do git — pega o estado atual da árvore, não o
 * commit de três meses atrás. Está registrado como lacuna na matriz de evidências.
 */
class NoVersionedSecretsTest {

    /** Onde código e config de verdade moram. Build, deps e binários ficam de fora. */
    private static final List<String> SCANNED_ROOTS =
            List.of("src", "docs", "litellm", ".github", "frontend/src");

    private static final List<String> SCANNED_FILES =
            List.of("pom.xml", "docker-compose.yml", ".env.example", "README.md");

    private static final Set<String> TEXT_EXTENSIONS =
            Set.of(".java", ".yml", ".yaml", ".xml", ".md", ".ts", ".tsx", ".css", ".json",
                    ".sql", ".csv", ".properties", ".example", ".sh");

    /**
     * Marcador de supressão por linha. Existe porque um teste de guardrail precisa de um
     * segredo com cara de real para provar que o detector funciona — e sem uma válvula
     * explícita a saída seria enfraquecer o detector, que é o pior dos mundos.
     *
     * <p>Deliberadamente por LINHA, e não por arquivo: cada exceção fica visível no diff e
     * precisa ser justificada onde está, em vez de sumir numa lista de exclusões que ninguém
     * revisa.
     */
    private static final String SUPPRESSION_MARKER = "NOSECRET";

    private final SecretDetector detector = new SecretDetector();

    @Test
    void repositorioNaoTemSegredoVersionado() throws IOException {
        List<String> hits = new ArrayList<>();

        for (String root : SCANNED_ROOTS) {
            Path dir = Path.of(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dir)) {
                files.filter(Files::isRegularFile)
                        .filter(NoVersionedSecretsTest::isTextFile)
                        .forEach(p -> scan(p, hits));
            }
        }
        for (String file : SCANNED_FILES) {
            Path p = Path.of(file);
            if (Files.isRegularFile(p)) {
                scan(p, hits);
            }
        }

        assertThat(hits)
                .as("segredo aparente versionado — mover para variável de ambiente antes do commit")
                .isEmpty();
    }

    /** O .env real nunca pode estar versionado, mesmo que não pareça conter segredo hoje. */
    @Test
    void arquivoEnvRealNaoEstaVersionado() throws IOException {
        Path gitignore = Path.of(".gitignore");
        assertThat(Files.readString(gitignore)).contains(".env");
    }

    private void scan(Path path, List<String> hits) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (MalformedInputException e) {
            return;                     // binário disfarçado de texto: não é alvo deste teste
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        String[] lines = content.split("\n", -1);
        for (DetectorMatch m : detector.find(content)) {
            int line = lineOf(content, m.start());
            if (lines[line - 1].contains(SUPPRESSION_MARKER)) {
                continue;
            }
            hits.add(path + " [" + m.label() + "] linha " + line);
        }
    }

    private static int lineOf(String content, int offset) {
        return (int) content.substring(0, Math.min(offset, content.length())).chars()
                .filter(c -> c == '\n').count() + 1;
    }

    private static boolean isTextFile(Path path) {
        String name = path.getFileName().toString();
        return TEXT_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}
