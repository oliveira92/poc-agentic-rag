package com.example.agenticrag.security;

import com.example.agenticrag.observability.RagMetrics;
import com.example.agenticrag.security.dataset.SecurityDataset;
import com.example.agenticrag.security.dataset.SecurityDatasetProperties;
import com.example.agenticrag.security.dataset.SecurityEvaluationService;
import com.example.agenticrag.security.detector.BinaryContentDetector;
import com.example.agenticrag.security.detector.PiiDetector;
import com.example.agenticrag.security.detector.PromptInjectionDetector;
import com.example.agenticrag.security.detector.ResourceOwnershipDetector;
import com.example.agenticrag.security.detector.SecretDetector;
import com.example.agenticrag.security.detector.SystemPromptLeakDetector;
import com.example.agenticrag.security.detector.ThirdPartyDataDetector;
import com.example.agenticrag.security.detector.UrlDetector;
import com.example.agenticrag.security.scope.LexicalScopeJudge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * Monta a pilha de guardrails <b>com a política real</b>, sem subir o contexto do Spring.
 *
 * <p>A política é lida do próprio {@code application.yml} de produção — não de uma cópia no
 * teste. Um teste de segurança que declara a sua própria configuração testa a configuração do
 * teste: aperta-se o YAML de produção, o teste continua verde, e a evidência passa a descrever
 * um sistema que não é o que está no ar. Aqui, mudar uma ação de MASK para BLOCK no arquivo de
 * produção muda o resultado do dataset na hora.
 *
 * <p>Sem contexto do Spring porque a alternativa exige Postgres e ONNX para exercitar regex —
 * o que tiraria estes testes do CI a cada PR, que é justamente quando eles valem.
 */
final class SecurityTestStack {

    private SecurityTestStack() {
    }

    /** Ambiente com o {@code application.yml} carregado e placeholders resolvíveis. */
    static StandardEnvironment environment() {
        StandardEnvironment env = new StandardEnvironment();
        try {
            List<PropertySource<?>> sources =
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
            sources.forEach(env.getPropertySources()::addLast);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler application.yml", e);
        }
        return env;
    }

    static SecurityProperties securityProperties() {
        return Binder.get(environment()).bind("app.security", SecurityProperties.class).get();
    }

    static SecurityDatasetProperties datasetProperties() {
        return Binder.get(environment())
                .bind("app.security-dataset", SecurityDatasetProperties.class).get();
    }

    static InputGuard inputGuard(SecurityProperties props) {
        GuardEngine engine = new GuardEngine(props, new RagMetrics(new SimpleMeterRegistry()));
        return new InputGuard(engine, props, new LexicalScopeJudge(props),
                new BinaryContentDetector(), new PromptInjectionDetector(),
                new ThirdPartyDataDetector(), new UrlDetector(props), new SecretDetector(),
                new PiiDetector(), new ResourceOwnershipDetector());
    }

    static OutputGuard outputGuard(SecurityProperties props) {
        GuardEngine engine = new GuardEngine(props, new RagMetrics(new SimpleMeterRegistry()));
        return new OutputGuard(engine, new PiiDetector(), new SecretDetector(),
                new SystemPromptLeakDetector());
    }

    static IngestionGuard ingestionGuard(SecurityProperties props) {
        GuardEngine engine = new GuardEngine(props, new RagMetrics(new SimpleMeterRegistry()));
        return new IngestionGuard(engine, props, new LexicalScopeJudge(props),
                new BinaryContentDetector(), new SecretDetector(), new PiiDetector(),
                new PromptInjectionDetector());
    }

    static SecurityEvaluationService evaluationService() {
        SecurityProperties props = securityProperties();
        SecurityDatasetProperties datasetProps = datasetProperties();
        SecurityDataset dataset = new SecurityDataset(
                new org.springframework.core.io.DefaultResourceLoader());
        return new SecurityEvaluationService(dataset, datasetProps, inputGuard(props));
    }
}
