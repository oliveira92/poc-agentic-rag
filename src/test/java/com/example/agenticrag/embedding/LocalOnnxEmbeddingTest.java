package com.example.agenticrag.embedding;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * O modelo de embedding quantizado (int8) é carregado do disco e ainda separa significado.
 *
 * <p>Trocar fp32 (448 MB) por int8 (112 MB) é uma troca de <b>precisão numérica por bytes</b>, e
 * uma troca dessas não pode ser aceita no diff — tem que ser medida. Aqui a medida é a que o RAG
 * de fato usa: <b>ordem</b>. Um par de textos sobre o mesmo assunto tem que ficar mais próximo do
 * que um par sobre assuntos diferentes, e isso precisa valer em <b>português</b>, que é a razão
 * de o projeto ter saído do MiniLM inglês.
 *
 * <p>Este teste não valida o número absoluto de similaridade — quantização mexe nele, e fixá-lo
 * seria criar uma âncora falsa que quebra a cada troca de peso sem indicar nada sobre qualidade.
 * O que se fixa é a relação de ordem, que é o que a recuperação consome.
 *
 * <p>Pula quando o cache está frio: um teste que baixa 112 MB não é um teste, é um download com
 * asserção. Rode {@code scripts/prefetch-embeddings.sh} para habilitá-lo.
 */
@EnabledIf("cacheQuente")
class LocalOnnxEmbeddingTest {

    private static EmbeddingProperties props;
    private static TransformersEmbeddingModel model;

    static boolean cacheQuente() {
        EmbeddingProperties p = defaults();
        return Files.isRegularFile(p.modelPath()) && Files.isRegularFile(p.tokenizerPath());
    }

    private static EmbeddingProperties defaults() {
        Path cache = Path.of(System.getProperty("user.home"), ".cache", "agentic-rag", "onnx");
        return new EmbeddingProperties("test", cache, "model_quantized.onnx", "tokenizer.json",
                "http://invalido/model.onnx", "http://invalido/tokenizer.json",
                "last_hidden_state", false);
    }

    @BeforeAll
    static void carregaDoDisco() throws Exception {
        props = defaults();
        model = new LocalOnnxEmbeddingConfig().embeddingModel(props, noRegistry());
    }

    @Test
    void produzVetorDe384DimensoesNormalizavel() {
        float[] v = model.embed("Como criar uma cobrança com o payments-sdk?");

        // 384 é contrato: é a dimensão da coluna vector do pgvector. Divergir aqui não degrada
        // a busca — quebra o INSERT.
        assertThat(v).hasSize(384);
        assertThat(norm(v)).isGreaterThan(0.0);
        for (float f : v) {
            assertThat(Float.isFinite(f)).as("componente não-finito no vetor").isTrue();
        }
    }

    /**
     * O critério que a recuperação realmente usa. Se a quantização tivesse destruído o sinal,
     * é aqui que apareceria — os pares deixariam de se ordenar.
     */
    @Test
    void aproximaAssuntosIguaisEAfastaAssuntosDiferentesEmPortugues() {
        float[] estorno = model.embed("como faço para estornar uma cobrança");
        float[] refund = model.embed("endpoint de reembolso de uma transação paga");
        float[] receita = model.embed("receita de bolo de cenoura com cobertura");

        double relacionados = cosine(estorno, refund);
        double naoRelacionados = cosine(estorno, receita);

        assertThat(relacionados)
                .as("estorno × reembolso (%.3f) tem que superar estorno × receita (%.3f)",
                        relacionados, naoRelacionados)
                .isGreaterThan(naoRelacionados);
        // Margem folgada de propósito: o que se afirma é que a ordem se mantém com alguma
        // distância, não um valor calibrado que a próxima troca de peso invalidaria.
        assertThat(relacionados - naoRelacionados).isGreaterThan(0.15);
    }

    /** O multilíngue é o motivo de o modelo ser grande — vale checar que ele serve para algo. */
    @Test
    void reconheceOMesmoConceitoEmPortuguesEIngles() {
        double crossLingual = cosine(
                model.embed("autenticação por token bearer"),
                model.embed("bearer token authentication"));
        double ruido = cosine(
                model.embed("autenticação por token bearer"),
                model.embed("previsão do tempo para amanhã"));

        assertThat(crossLingual).isGreaterThan(ruido);
    }

    /**
     * O caminho de escrita e o de leitura não são o mesmo: a ingestão embeda em <b>lote</b>, a
     * busca embeda a pergunta <b>sozinha</b>. Se os dois divergissem muito, a recuperação
     * degradaria sem nenhum erro aparecer em lugar nenhum.
     *
     * <p>Eles divergem um pouco — medido: <b>0,992</b> de cosseno. A causa é o padding: em lote
     * as sequências são preenchidas até a mais longa, e o pooling não sai idêntico ao da frase
     * isolada. É efeito de batching, não da quantização (o mesmo aparece em fp32).
     *
     * <p>O piso é 0,98 e não 0,999 por isso. E a diferença é irrelevante na prática: 0,008 é uma
     * ordem de grandeza abaixo da distância entre assunto relacionado e não relacionado
     * (&gt;0,15 no teste acima) — não chega perto de inverter uma ordenação.
     */
    @Test
    void embeddingEmLoteBateComOIndividual() {
        String texto = "rate limit por token no payments-sdk";
        List<float[]> lote = model.embed(List.of(texto, "outro texto qualquer"));

        assertThat(cosine(lote.get(0), model.embed(texto))).isGreaterThan(0.98);
    }

    /** Cache frio com {@code require-local} é erro de pipeline, e a mensagem tem que dizer isso. */
    @Test
    void cacheFrioComRequireLocalFalhaComOComandoDeCorrecao() {
        EmbeddingProperties semCache = new EmbeddingProperties("test",
                Path.of("/tmp/cache-que-nao-existe-" + System.nanoTime()),
                "model_quantized.onnx", "tokenizer.json",
                "http://invalido/m.onnx", "http://invalido/t.json", "last_hidden_state", true);

        assertThatThrownBy(() -> new LocalOnnxEmbeddingConfig().embeddingModel(semCache, noRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prefetch-embeddings.sh");
    }

    /** Arquivo truncado (queda de rede) tem que virar mensagem, não erro do runtime nativo. */
    @Test
    void arquivoTruncadoEhDetectadoAntesDeCarregarOOnnx() throws Exception {
        Path dir = Files.createTempDirectory("onnx-truncado");
        Files.writeString(dir.resolve("model_quantized.onnx"), "download pela metade");
        Files.writeString(dir.resolve("tokenizer.json"), "{}");

        EmbeddingProperties truncado = new EmbeddingProperties("test", dir,
                "model_quantized.onnx", "tokenizer.json",
                "http://invalido/m.onnx", "http://invalido/t.json", "last_hidden_state", false);

        assertThatThrownBy(() -> new LocalOnnxEmbeddingConfig().embeddingModel(truncado, noRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("interrompido");
    }

    // ---------- helpers ----------

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot / (norm(a) * norm(b));
    }

    private static double norm(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += f * f;
        }
        return Math.sqrt(sum);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ObservationRegistry> noRegistry() {
        return (ObjectProvider<ObservationRegistry>) java.lang.reflect.Proxy.newProxyInstance(
                LocalOnnxEmbeddingTest.class.getClassLoader(),
                new Class<?>[] { ObjectProvider.class },
                (proxy, method, args) -> "getIfUnique".equals(method.getName())
                        ? ObservationRegistry.NOOP : null);
    }
}
