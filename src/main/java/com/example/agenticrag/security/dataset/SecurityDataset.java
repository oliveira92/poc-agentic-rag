package com.example.agenticrag.security.dataset;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Carrega o dataset do CSV. Separador {@code ;} porque as perguntas contêm vírgula, e linhas
 * iniciadas por {@code #} são comentário — o arquivo é lido por humanos tanto quanto por código.
 *
 * <p>O dataset é <b>recurso da aplicação</b>, não do teste, de propósito: ele alimenta tanto o
 * JUnit quanto o endpoint {@code POST /api/v1/security/evaluate}, então a demo executa
 * exatamente os mesmos casos que o CI — sem uma segunda cópia para divergir.
 */
@Component
public class SecurityDataset {

    private final ResourceLoader resourceLoader;

    public SecurityDataset(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    public List<SecurityCase> load(SecurityDatasetProperties.Scenario scenario) {
        return load(scenario.resource());
    }

    public List<SecurityCase> load(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Dataset de segurança não encontrado: " + location);
        }
        List<SecurityCase> cases = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("id;")) {
                    continue;
                }
                String[] parts = trimmed.split(";", 3);
                if (parts.length == 3) {
                    cases.add(new SecurityCase(parts[0].strip(), parts[1].strip(), parts[2].strip()));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler o dataset " + location, e);
        }
        if (cases.isEmpty()) {
            throw new IllegalStateException("Dataset de segurança vazio: " + location);
        }
        return List.copyOf(cases);
    }
}
