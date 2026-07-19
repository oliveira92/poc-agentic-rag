package com.example.agenticrag.advisor;

import com.example.agenticrag.ai.ChatResponses;
import com.example.agenticrag.domain.model.KnowledgeSource;
import com.example.agenticrag.infra.persistence.KnowledgeApprovalRepository;
import com.example.agenticrag.model.ModelCatalogService;
import com.example.agenticrag.observability.CostProperties;
import com.example.agenticrag.observability.RagMetrics;
import com.example.agenticrag.quality.CitationGroundingChecker;
import com.example.agenticrag.routing.Route;
import com.example.agenticrag.routing.RouteClassifier;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Coração da Fase 1 (Structured RAG), agora instrumentado com as 5 camadas do M03.
 *
 * <p>Fluxo: classifica a rota/risco (A05) -> recupera do pgvector (memória de longo prazo) os
 * chunks do componente -> monta contexto com citações -> pede à LLM (modelo escolhido por
 * risco) uma explicação de "como consumir" ancorada apenas nesse contexto -> aplica memória
 * de curto prazo (conversa) -> checa fidelidade das citações (A03) -> salva rascunho para
 * aprovação humana. Em todo o caminho emite SLIs técnicos (A02), eventos e tags no trace (A01).
 *
 * <p>Optamos por recuperação MANUAL (em vez do QuestionAnswerAdvisor) para devolver
 * citações estruturadas e ter controle explícito sobre filtro por componente e rerank.
 */
@Service
public class ComponentAdvisorService {

    private static final int TOP_K = 6;

    /**
     * Teto de texto POR FONTE no contexto enviado à LLM. Separado do snippet de exibição
     * (600 chars): um doc LLM_APPROVED é "Pergunta: ...\n\nResposta aprovada: ..." e, com o
     * teto curto, a resposta era cortada — o modelo recebia a promessa sem o conteúdo
     * (falha real observada: "o item [1] veio vazio"). 2000 chars × top-6 ≈ 3k tokens de
     * input — custo visível em rag.llm.tokens (A04).
     */
    private static final int MAX_CONTEXT_CHARS_PER_SOURCE = 2000;

    /**
     * Máximo de docs LLM_APPROVED no top-K (rerank por fonte — HU-08). Regressão real pega
     * pelo quality gate: Q&As aprovados grandes tomaram o top-6 e empurraram fontes
     * primárias para fora (g07 caiu). A precedência "PORTAL_API/README > LLM_APPROVED" que
     * o prompt declara passa a valer TAMBÉM na seleção da recuperação.
     */
    private static final int MAX_APPROVED_IN_TOP_K = 2;

    /**
     * Bônus léxico quando um termo da pergunta aparece no TÍTULO da fonte (ref) — sinal
     * barato estilo "BM25 nos títulos", precursor da busca híbrida da Fase 2. Caso real:
     * "Como os erros desta API são formatados?" — o denso dava só 0.299 para a seção
     * "README > Erros" (abaixo de overview/auth genéricos); o título casa literalmente.
     */
    private static final double TITLE_MATCH_BONUS = 0.12;

    private static final int MIN_TERM_LEN = 4;

    private static final String SYSTEM_PROMPT = """
            Você é um engenheiro de plataforma que orienta desenvolvedores a CONSUMIR componentes internos.
            Regras:
            - Responda APENAS com base no CONTEXTO fornecido. Não invente endpoints, campos ou headers.
            - Se o contexto for insuficiente, diga explicitamente o que falta ingerir.
            - Cite as fontes usando os marcadores [n] correspondentes ao contexto.
            - Seja prático: mostre passo a passo, exemplos de request/response e cuidados (auth, idempotência, rate limit, erros).
            - Fontes 'PORTAL_API' e 'README' têm precedência sobre 'LLM_APPROVED' em caso de conflito.
            """;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final KnowledgeApprovalRepository approvals;
    private final RagMetrics metrics;
    private final RouteClassifier router;
    private final CitationGroundingChecker citationChecker;
    private final ModelCatalogService modelCatalog;
    private final CostProperties cost;
    private final ObjectProvider<Tracer> tracerProvider;

    public ComponentAdvisorService(ChatClient chatClient,
                                   VectorStore vectorStore,
                                   KnowledgeApprovalRepository approvals,
                                   RagMetrics metrics,
                                   RouteClassifier router,
                                   CitationGroundingChecker citationChecker,
                                   ModelCatalogService modelCatalog,
                                   CostProperties cost,
                                   ObjectProvider<Tracer> tracerProvider) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.approvals = approvals;
        this.metrics = metrics;
        this.router = router;
        this.citationChecker = citationChecker;
        this.modelCatalog = modelCatalog;
        this.cost = cost;
        this.tracerProvider = tracerProvider;
    }

    /**
     * Recuperação semântica pura (sem LLM): retorna as citações do componente para uma
     * consulta. Útil para transparência/inspeção e reaproveitado pelo {@link #advise}.
     */
    public List<Citation> retrieve(String componentId, String question, int topK) {
        Route route = router.classify(componentId, question);
        return retrieveTimed(componentId, question, topK, route).citations();
    }

    /**
     * @param requestedModel modelo escolhido explicitamente pelo cliente (opcional). Se informado,
     *                       é validado contra o catálogo e tem precedência sobre o roteamento por
     *                       risco; se ausente/vazio, o {@link RouteClassifier} decide o modelo.
     */
    public AdviceResult advise(String componentId, String question, String conversationId, String requestedModel) {
        String cid = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        Route route = router.classify(componentId, question);

        // Seleção de modelo: override manual validado (400 se inválido) OU o modelo da rota (A05).
        String override = modelCatalog.resolve(requestedModel);
        String model = override != null ? override : route.model();
        String modelSource = override != null ? "requested" : "route";

        Span span = currentSpan();
        tagRoute(span, componentId, route, model, modelSource);

        long t0 = System.nanoTime();
        String outcome = "success";
        try {
            // 1) Recuperação semântica filtrada pelo componente (memória de longo prazo)
            Retrieval retrieval = retrieveTimed(componentId, question, TOP_K, route);
            List<Citation> citations = retrieval.citations();
            boolean grounded = !citations.isEmpty();
            if (!grounded) {
                metrics.incrementUngrounded(route.tag());
                event(span, "rag.ungrounded");
            }
            // Contexto da LLM usa o texto (quase) completo dos docs — não o snippet de exibição.
            String context = buildContext(retrieval.docs());

            // 2) Geração ancorada no contexto + memória de curto prazo. Modelo escolhido pela rota (A05).
            String userMessage = """
                    Componente: %s
                    Pergunta: %s

                    CONTEXTO (fontes numeradas):
                    %s
                    """.formatted(componentId, question,
                    grounded ? context : "(nenhum contexto recuperado — a base para este componente pode não ter sido ingerida)");

            ChatResponse response = chatClient.prompt()
                    .options(ChatOptions.builder().model(model))
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                    .system(SYSTEM_PROMPT)
                    .user(userMessage)
                    .call()
                    .chatResponse();

            // Claude 5 + thinking: o texto final é a ÚLTIMA Generation (a 1ª pode ser raciocínio).
            String answer = ChatResponses.answerText(response);
            recordUsage(span, model, response);

            // 3) Guardrail de fidelidade determinístico (A03): endpoints citados constam das fontes?
            CitationGroundingChecker.Result check = citationChecker.check(answer, citations);
            if (check.lowConfidence()) {
                metrics.incrementLowConfidence(route.tag());
                event(span, "rag.low_confidence");
                tag(span, "rag.unsupported_endpoints", String.join(",", check.unsupportedEndpoints()));
            }

            // 4) Persiste rascunho para o loop de aprovação humana (Corrective/Feedback RAG).
            //    Resposta vazia NÃO vira rascunho: já houve caso de vazio aprovado e indexado,
            //    envenenando a recuperação (o guard espelho existe no approve).
            UUID approvalId = null;
            if (answer != null && !answer.isBlank()) {
                approvalId = approvals.savePending(componentId, question, answer);
            } else {
                metrics.incrementError(model, "empty_answer");
                event(span, "rag.empty_answer");
            }

            return new AdviceResult(componentId, cid, answer, citations, grounded, approvalId,
                    traceId(span), route.tag(), model, check.lowConfidence(), check.unsupportedEndpoints());
        } catch (RuntimeException e) {
            outcome = classifyError(e);
            metrics.incrementError(model, outcome);
            event(span, "rag.error");
            throw e;
        } finally {
            metrics.recordAdvise(route.tag(), model, outcome, System.nanoTime() - t0);
        }
    }

    // ---------- recuperação instrumentada ----------

    /** Docs crus (contexto da LLM) + citações (exibição/envelope), na mesma ordem/índice. */
    private record Retrieval(List<Document> docs, List<Citation> citations) {
    }

    private Retrieval retrieveTimed(String componentId, String question, int topK, Route route) {
        String safeComponentId = requireSafe(componentId);
        long t0 = System.nanoTime();
        // Over-fetch (2×K) para os reranks (léxico + fonte) terem de onde repor docs.
        List<Document> fetched = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(topK * 2)
                .filterExpression("component_id == '" + safeComponentId + "'")
                .build());
        List<Document> docs = selectWithPrecedence(
                rerankByTitleMatch(fetched, question), topK, MAX_APPROVED_IN_TOP_K);
        List<Citation> citations = toCitations(docs);
        metrics.recordRetrieve(route.tag(), System.nanoTime() - t0);
        if (!citations.isEmpty() && citations.get(0).score() != null) {
            metrics.recordTopScore(route.tag(), citations.get(0).score());
        }
        return new Retrieval(docs, citations);
    }

    /**
     * Rerank léxico leve: soma {@link #TITLE_MATCH_BONUS} ao score quando algum termo da
     * pergunta (≥{@value #MIN_TERM_LEN} letras, sem acentos) aparece no título/ref da fonte.
     * Ordenação estável — sem casamento, a ordem do denso é preservada.
     */
    static List<Document> rerankByTitleMatch(List<Document> ranked, String question) {
        Set<String> queryTerms = significantTerms(question);
        if (queryTerms.isEmpty()) {
            return ranked;
        }
        record Scored(Document doc, double effective) {
        }
        List<Scored> scored = new ArrayList<>(ranked.size());
        for (Document d : ranked) {
            double base = d.getScore() == null ? 0.0 : d.getScore();
            String ref = normalize(refOf(d));
            boolean titleMatch = queryTerms.stream().anyMatch(ref::contains);
            scored.add(new Scored(d, base + (titleMatch ? TITLE_MATCH_BONUS : 0.0)));
        }
        scored.sort(Comparator.comparingDouble(Scored::effective).reversed());
        return scored.stream().map(Scored::doc).toList();
    }

    /** Termos relevantes da pergunta: minúsculos, sem acentos, com {@value #MIN_TERM_LEN}+ letras. */
    private static Set<String> significantTerms(String question) {
        Set<String> terms = new HashSet<>();
        if (question == null) {
            return terms;
        }
        for (String raw : normalize(question).split("[^\\p{L}\\p{Nd}]+")) {
            if (raw.length() >= MIN_TERM_LEN) {
                terms.add(raw);
            }
        }
        return terms;
    }

    /** minúsculas + remoção de diacríticos ("Idempotência" → "idempotencia"). */
    private static String normalize(String s) {
        String lower = s == null ? "" : s.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    /**
     * Rerank por fonte (HU-08): mantém a ordem por score, mas limita docs LLM_APPROVED a
     * {@code maxApproved} vagas no top-K — conhecimento aprovado complementa, não expulsa
     * as fontes primárias (PORTAL_API/README) do contexto.
     */
    static List<Document> selectWithPrecedence(List<Document> ranked, int topK, int maxApproved) {
        List<Document> selected = new ArrayList<>(Math.min(topK, ranked.size()));
        int approved = 0;
        for (Document d : ranked) {
            if (selected.size() >= topK) {
                break;
            }
            boolean isApproved = KnowledgeSource.LLM_APPROVED.name()
                    .equals(String.valueOf(d.getMetadata().get(KnowledgeSource.METADATA_KEY)));
            if (isApproved) {
                if (approved >= maxApproved) {
                    continue;   // vaga de aprovado esgotada — segue para a próxima primária
                }
                approved++;
            }
            selected.add(d);
        }
        return selected;
    }

    // ---------- observabilidade (A01/A02/A04) ----------

    private void recordUsage(Span span, String model, ChatResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        Integer in = usage.getPromptTokens();
        Integer out = usage.getCompletionTokens();
        metrics.recordTokens(model, in, out);
        double estimated = cost.estimate(in, out, model);   // preço por família de modelo (A04)
        if (estimated > 0) {
            metrics.recordCost(model, estimated);
        }
        tag(span, "gen_ai.usage.input_tokens", String.valueOf(in));
        tag(span, "gen_ai.usage.output_tokens", String.valueOf(out));
    }

    private Span currentSpan() {
        Tracer tracer = tracerProvider.getIfAvailable();
        return tracer == null ? null : tracer.currentSpan();
    }

    private void tagRoute(Span span, String componentId, Route route, String model, String modelSource) {
        tag(span, "rag.component_id", componentId);
        tag(span, "rag.route", route.tag());
        tag(span, "rag.risk", route.risk().name());
        tag(span, "rag.model_source", modelSource);   // "requested" (seleção manual) ou "route"
        tag(span, "gen_ai.request.model", model);
    }

    private static void tag(Span span, String key, String value) {
        if (span != null && value != null) {
            span.tag(key, value);
        }
    }

    private static void event(Span span, String name) {
        if (span != null) {
            span.event(name);
        }
    }

    private static String traceId(Span span) {
        return span == null ? null : span.context().traceId();
    }

    /** Tipos de erro têm ações diferentes (A02): 429=rotear/backoff, timeout=fallback rápido. */
    private static String classifyError(RuntimeException e) {
        String name = e.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        if (name.contains("ratelimit") || msg.contains("429") || msg.contains("rate limit")) {
            return "rate_limit";
        }
        if (name.contains("timeout") || msg.contains("timeout") || msg.contains("timed out")) {
            return "timeout";
        }
        return "other";
    }

    // ---------- helpers de citação/contexto ----------

    private List<Citation> toCitations(List<Document> docs) {
        List<Citation> citations = new ArrayList<>(docs.size());
        int i = 1;
        for (Document d : docs) {
            String source = String.valueOf(d.getMetadata().getOrDefault(KnowledgeSource.METADATA_KEY, "?"));
            String ref = refOf(d);
            citations.add(new Citation(i++, source, ref, d.getScore(), snippet(d.getText())));
        }
        return citations;
    }

    private static String refOf(Document d) {
        var md = d.getMetadata();
        if (md.containsKey("endpoint")) {
            return String.valueOf(md.get("endpoint"));
        }
        if (md.containsKey("section")) {
            return "README > " + md.get("section");
        }
        return String.valueOf(md.getOrDefault("kind", "doc"));
    }

    /** Contexto da LLM: mesmos índices [n] das citações, mas com o texto quase completo. */
    private static String buildContext(List<Document> docs) {
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Document d : docs) {
            String source = String.valueOf(d.getMetadata().getOrDefault(KnowledgeSource.METADATA_KEY, "?"));
            sb.append('[').append(i++).append("] (").append(source).append(" - ")
                    .append(refOf(d)).append(")\n")
                    .append(cap(d.getText(), MAX_CONTEXT_CHARS_PER_SOURCE)).append("\n\n");
        }
        return sb.toString();
    }

    /** Snippet curto para exibição (UI/API) — não é o que a LLM recebe. */
    private static String snippet(String text) {
        return cap(text, 600);
    }

    private static String cap(String text, int max) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= max ? t : t.substring(0, max) + " …";
    }

    private static String requireSafe(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("component_id inválido: " + id);
        }
        return id;
    }
}
