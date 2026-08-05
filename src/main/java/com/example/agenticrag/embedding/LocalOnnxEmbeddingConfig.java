package com.example.agenticrag.embedding;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Constrói o modelo de embedding local resolvendo <b>arquivo primeiro, rede depois</b>.
 *
 * <p>Substitui a auto-config do Spring AI (que é {@code @ConditionalOnMissingBean}) por dois
 * motivos que a configuração por properties não resolve:
 *
 * <ul>
 *   <li><b>Saber, e dizer, se houve rede.</b> A auto-config baixa em silêncio; num link de
 *       200 KB/s isso é a diferença entre um boot de 9 segundos e um de 11 minutos, e hoje não há
 *       nada no log que diferencie os dois até que já seja tarde. Aqui o caminho escolhido vira
 *       uma linha de log com o tamanho do arquivo.</li>
 *   <li><b>Falhar rápido quando o cache devia estar quente.</b> Em CI e em contêiner, cache frio é
 *       defeito de pipeline, não motivo para baixar 112 MB. Com {@code require-local=true} o boot
 *       para com o comando de correção em vez de travar.</li>
 * </ul>
 *
 * <p>Desligado quando {@code spring.ai.model.embedding} não é {@code transformers} — nesse caso o
 * embedding vem do gateway LiteLLM e não há modelo nenhum no processo (ver ADR-0009).
 */
@Configuration
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "transformers",
        matchIfMissing = true)
public class LocalOnnxEmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(LocalOnnxEmbeddingConfig.class);

    @Bean
    public TransformersEmbeddingModel embeddingModel(EmbeddingProperties props,
                                                     ObjectProvider<ObservationRegistry> registry)
            throws Exception {

        TransformersEmbeddingModel model = new TransformersEmbeddingModel(
                MetadataMode.NONE, registry.getIfUnique(() -> ObservationRegistry.NOOP));

        model.setModelResource(resolve(props, props.modelPath(), props.modelUrl(), "modelo"));
        model.setTokenizerResource(
                resolve(props, props.tokenizerPath(), props.tokenizerUrl(), "tokenizer"));
        model.setModelOutputName(props.modelOutputName());
        // Só importa no fallback de rede: é onde o download é depositado para o próximo boot.
        model.setResourceCacheDirectory(props.cacheDir().toString());
        model.afterPropertiesSet();
        return model;
    }

    /**
     * Arquivo local se existir; senão a URL remota — ou uma falha explicando o que fazer.
     *
     * <p>O caso "existe mas está truncado" é tratado junto: um download interrompido deixa um
     * arquivo pequeno que carregaria como ONNX inválido, com uma exceção do runtime nativo que
     * não diz nada sobre a causa. Um piso de tamanho transforma isso na mensagem certa.
     */
    private static String resolve(EmbeddingProperties props, Path local, String remoteUrl,
                                  String what) throws IOException {
        if (Files.isRegularFile(local)) {
            long bytes = Files.size(local);
            if (bytes < 1_000_000) {
                throw new IllegalStateException(("""
                        %s de embedding em %s tem só %d bytes — download interrompido. \
                        Apague o arquivo e rode: scripts/prefetch-embeddings.sh""")
                        .formatted(what, local, bytes));
            }
            log.info("Embedding: {} do cache local {} ({} MB) — sem rede no boot.",
                    what, local, bytes / (1024 * 1024));
            return "file:" + local.toAbsolutePath();
        }

        if (props.requireLocal()) {
            throw new IllegalStateException(("""
                    %s de embedding ausente em %s e app.embedding.require-local=true. \
                    O cache devia estar quente aqui (CI/contêiner). \
                    Rode scripts/prefetch-embeddings.sh ou restaure o cache do pipeline.""")
                    .formatted(what, local));
        }

        log.warn("Embedding: {} NÃO está em {} — baixando de {}. "
                        + "Primeiro boot é lento; rode scripts/prefetch-embeddings.sh "
                        + "para fazer isso uma vez, de forma resumível.",
                what, local, hostOf(remoteUrl));
        return remoteUrl;
    }

    /** Só o host, para o log não virar uma URL de 120 caracteres. */
    private static String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost().toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return url;
        }
    }
}
