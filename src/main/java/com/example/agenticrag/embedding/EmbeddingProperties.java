package com.example.agenticrag.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * De onde vem o modelo de embedding local — e, principalmente, de onde ele <b>não</b> vem no boot.
 *
 * <p>O problema medido nesta máquina: o Spring AI baixa o ONNX do HuggingFace para
 * {@code ${java.io.tmpdir}/spring-ai-onnx-generative}. Em macOS esse diretório é purgado pelo
 * sistema; em contêiner e em CI ele nunca existe. Resultado: o que parecia "baixa uma vez" era
 * na prática <b>baixa de novo a cada boot frio</b> — 465 MB a 200 KB/s, ~39 minutos.
 *
 * <p>Duas mudanças resolvem, e nenhuma delas é trocar de fornecedor:
 * <ol>
 *   <li><b>Cache fora do tmp</b> ({@code ~/.cache/agentic-rag/onnx}), versionado por modelo, que
 *       sobrevive a reboot e é compartilhado entre clones do repositório;</li>
 *   <li><b>Peso int8</b> em vez de fp32: 112 MB em vez de 448 MB, mesmas 384 dimensões e mesmo
 *       tokenizer — nenhuma migração de schema, nenhuma reindexação obrigatória.</li>
 * </ol>
 *
 * <p>{@code requireLocal} existe porque falha silenciosa aqui é cara: sem ele, um cache frio em
 * CI ou em contêiner vira um boot de 11 minutos que ninguém entende. Com ele, vira uma mensagem
 * dizendo qual comando rodar.
 *
 * @param modelId         id lógico do modelo; entra no hash de idempotência da ingestão, então
 *                        mudá-lo força re-embed — é o que impede vetores de modelos diferentes
 *                        de conviverem na mesma tabela
 * @param cacheDir        onde os arquivos ficam (persistente, NUNCA {@code java.io.tmpdir})
 * @param modelFile       nome do arquivo do modelo dentro de {@code cacheDir}
 * @param tokenizerFile   nome do arquivo do tokenizer dentro de {@code cacheDir}
 * @param modelUrl        origem remota do modelo — aponte para um mirror interno se tiver um
 * @param tokenizerUrl    origem remota do tokenizer
 * @param modelOutputName tensor de saída do ONNX
 * @param requireLocal    exigir cache quente; {@code true} falha rápido em vez de baixar no boot
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingProperties(
        @DefaultValue("unknown") String modelId,
        Path cacheDir,
        @DefaultValue("model_quantized.onnx") String modelFile,
        @DefaultValue("tokenizer.json") String tokenizerFile,
        String modelUrl,
        String tokenizerUrl,
        @DefaultValue("last_hidden_state") String modelOutputName,
        @DefaultValue("false") boolean requireLocal) {

    public Path modelPath() {
        return cacheDir.resolve(modelFile);
    }

    public Path tokenizerPath() {
        return cacheDir.resolve(tokenizerFile);
    }
}
