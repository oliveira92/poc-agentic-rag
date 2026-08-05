package com.example.agenticrag.embedding;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quanto custou, em qualidade, trocar o peso fp32 (448 MB) pelo int8 (112 MB).
 *
 * <p>Trocar 4× menos bytes por "provavelmente não muda nada" é o tipo de decisão que envelhece
 * mal. Este teste responde com número: carrega os <b>dois</b> pesos lado a lado e compara o que
 * a recuperação de fato consome — a <b>ordem</b> dos vizinhos, não o valor absoluto do cosseno.
 *
 * <p>Pula quando o peso fp32 não está no cache, que é o caso normal: ninguém deveria precisar
 * dos 448 MB para rodar o projeto. Para reproduzir a medição:
 *
 * <pre>{@code
 * curl -L -C - -o ~/.cache/agentic-rag/onnx/model_fp32_reference.onnx \
 *   https://huggingface.co/Xenova/paraphrase-multilingual-MiniLM-L12-v2/resolve/main/onnx/model.onnx
 * mvn -Dtest=QuantizationFidelityTest test
 * }</pre>
 */
@EnabledIf("referenciaFp32Disponivel")
class QuantizationFidelityTest {

    private static final Logger log = LoggerFactory.getLogger(QuantizationFidelityTest.class);

    private static final Path CACHE =
            Path.of(System.getProperty("user.home"), ".cache", "agentic-rag", "onnx");

    /**
     * Pares consulta → resposta correta, no domínio do projeto. O que se cobra do modelo é
     * <b>acertar</b> o par, não concordar com o fp32: um teste de concordância aprovaria dois
     * modelos igualmente ruins.
     */
    private static final List<String[]> PARES = List.of(
            new String[] { "como faço para estornar uma cobrança",
                           "endpoint de reembolso de uma transação paga" },
            new String[] { "autenticação por token bearer",
                           "bearer token authentication" },
            new String[] { "rate limit por token no payments-sdk",
                           "limite de requisições por minuto por credencial" });

    /** Distratores: existem para o vizinho correto ter com quem competir. */
    private static final List<String> DISTRATORES = List.of(
            "receita de bolo de cenoura com cobertura",
            "previsão do tempo para amanhã",
            "o payload do webhook inclui o e-mail do cliente");

    // Ficaram de fora os pares de idempotência: num corpus de 9 frases curtas, "chave de
    // idempotência" não tem vizinho inequívoco — o fp32 também erra. Cobrar isso mediria o
    // tamanho do corpus, não a quantização. Idempotência é coberta ponta a ponta no
    // ComponentIngestionIT, contra a base real.

    private static final List<String> CORPUS = java.util.stream.Stream.concat(
            PARES.stream().flatMap(java.util.Arrays::stream), DISTRATORES.stream()).toList();

    private static TransformersEmbeddingModel int8;
    private static TransformersEmbeddingModel fp32;

    /** Tamanho exato do peso fp32 publicado — ver {@code scripts/prefetch-embeddings.sh}. */
    private static final long FP32_BYTES = 470_268_510L;

    static boolean referenciaFp32Disponivel() {
        Path ref = CACHE.resolve("model_fp32_reference.onnx");
        try {
            // Tamanho EXATO, não um piso: com piso, um download ainda em andamento passa da
            // marca e o teste roda contra um arquivo parcial — o ONNX falha no parse do protobuf
            // e o erro não diz nada sobre a causa real. Aconteceu ao escrever este teste.
            return Files.isRegularFile(ref) && Files.size(ref) == FP32_BYTES;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    static void carregaOsDoisPesos() throws Exception {
        int8 = load("model_quantized.onnx");
        fp32 = load("model_fp32_reference.onnx");
    }

    /**
     * A métrica que importa: o vetor int8 aponta para o mesmo lugar que o fp32?
     *
     * <p>Cosseno entre o mesmo texto embedado pelos dois pesos. 1,0 seria idêntico; o que se
     * exige é alto o bastante para não reordenar vizinhos.
     */
    @Test
    void vetoresInt8ApontamParaOMesmoLugarQueOsFp32() {
        double pior = 1.0;
        double soma = 0;
        for (String texto : CORPUS) {
            double sim = cosine(int8.embed(texto), fp32.embed(texto));
            soma += sim;
            pior = Math.min(pior, sim);
            log.info("int8×fp32  {}  \"{}\"", fmt(sim), texto);
        }
        log.info("int8×fp32 média={} pior={}", fmt(soma / CORPUS.size()), fmt(pior));

        // Medido: média 0,993, pior caso 0,980. O piso é 0,97 — abaixo disso a quantização
        // estaria mexendo no vetor o bastante para valer a pena reconsiderar a troca.
        assertThat(pior)
                .as("pior caso de fidelidade int8 vs fp32 no corpus do domínio")
                .isGreaterThan(0.97);
    }

    /** A referência precisa estar sã antes de servir de régua para qualquer coisa. */
    @Test
    void fp32RecuperaTodosOsParesCorretamente() {
        for (String[] par : PARES) {
            assertThat(maisProximo(fp32, par[0]))
                    .as("fp32 falhou \"%s\" — problema do corpus, não da quantização", par[0])
                    .isEqualTo(par[1]);
        }
    }

    /**
     * <b>O que a quantização custou, em número.</b>
     *
     * <p>Aqui está a resposta honesta: int8 <b>não</b> é de graça. Em 3 pares do domínio, ele
     * acerta 2 e erra 1 — "rate limit por token no payments-sdk" deixa de recuperar "limite de
     * requisições por minuto por credencial". O fp32 acerta os 3.
     *
     * <p>O teste fixa esse custo em vez de escondê-lo: exige a maioria e registra o que caiu.
     * Se um peso futuro acertar os 3, alguém vai ver no log e poderá apertar o piso; se cair
     * para 1, o build para. Um teste que só afirmasse "int8 é bom o bastante" não distinguiria
     * os dois casos.
     *
     * <p>É também o argumento mais concreto a favor do embedding pelo gateway (ADR-0009): lá não
     * há quantização, e este custo não existe.
     */
    @Test
    void int8AcertaAMaioriaDosParesEOQueEleErraFicaRegistrado() {
        int acertos = 0;
        for (String[] par : PARES) {
            String escolha = maisProximo(int8, par[0]);
            if (escolha.equals(par[1])) {
                acertos++;
            } else {
                log.warn("CUSTO DA QUANTIZAÇÃO: \"{}\" -> int8 escolheu \"{}\", esperado \"{}\"",
                        par[0], escolha, par[1]);
            }
        }
        log.info("int8 acertou {}/{} pares do domínio (fp32 acerta {}/{})",
                acertos, PARES.size(), PARES.size(), PARES.size());

        assertThat(acertos)
                .as("int8 perdeu recall além do custo já medido (2/3)")
                .isGreaterThanOrEqualTo(2);
    }

    /**
     * De onde vem a divergência lote × individual medida em {@link LocalOnnxEmbeddingTest}.
     *
     * <p>A hipótese natural é padding: em lote as sequências são preenchidas até a mais longa.
     * <b>A medição desmente.</b> O fp32 dá 1,00000 — o mesmo vetor, em lote ou sozinho. Só o int8
     * diverge (0,989), e a razão é específica da <b>quantização dinâmica</b>: as escalas de
     * ativação são calculadas a partir do tensor que está sendo processado, então a mesma frase
     * acompanhada de outra recebe escalas diferentes.
     *
     * <p>Importa porque os dois caminhos existem no sistema: a ingestão embeda em lote e a busca
     * embeda a pergunta sozinha. O efeito (~0,011) é uma ordem de grandeza menor que a distância
     * entre relacionado e não relacionado, e o teste de pares acima confirma que não muda quem é
     * recuperado — mas é um custo real da quantização, e é dela, não do batching.
     */
    @Test
    void divergenciaEntreLoteEIndividualVemDaQuantizacaoNaoDoPadding() {
        String texto = "rate limit por token no payments-sdk";
        List<String> lote = List.of(texto, "outro texto qualquer bem mais longo que o primeiro");

        double deltaInt8 = cosine(int8.embed(lote).get(0), int8.embed(texto));
        double deltaFp32 = cosine(fp32.embed(lote).get(0), fp32.embed(texto));
        log.info("lote×individual  int8={}  fp32={}", fmt(deltaInt8), fmt(deltaFp32));

        assertThat(deltaFp32).as("fp32 é insensível ao batching").isGreaterThan(0.9999);
        assertThat(deltaInt8).as("int8 diverge, mas longe de reordenar").isGreaterThan(0.98);
    }

    // ---------- helpers ----------

    private static String maisProximo(TransformersEmbeddingModel model, String consulta) {
        float[] q = model.embed(consulta);
        String melhor = null;
        double melhorSim = -2;
        for (String candidato : CORPUS) {
            if (candidato.equals(consulta)) {
                continue;
            }
            double sim = cosine(q, model.embed(candidato));
            if (sim > melhorSim) {
                melhorSim = sim;
                melhor = candidato;
            }
        }
        return melhor;
    }

    private static TransformersEmbeddingModel load(String modelFile) throws Exception {
        EmbeddingProperties props = new EmbeddingProperties("fidelity", CACHE, modelFile,
                "tokenizer.json", "http://invalido/m", "http://invalido/t",
                "last_hidden_state", true);
        return new LocalOnnxEmbeddingConfig().embeddingModel(props, noRegistry());
    }

    private static double cosine(float[] a, float[] b) {
        double dot = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.5f", d);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ObservationRegistry> noRegistry() {
        return (ObjectProvider<ObservationRegistry>) java.lang.reflect.Proxy.newProxyInstance(
                QuantizationFidelityTest.class.getClassLoader(),
                new Class<?>[] { ObjectProvider.class },
                (proxy, method, args) -> "getIfUnique".equals(method.getName())
                        ? ObservationRegistry.NOOP : null);
    }
}
