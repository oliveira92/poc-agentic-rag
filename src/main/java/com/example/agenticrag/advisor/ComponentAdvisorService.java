package com.example.agenticrag.advisor;

import com.example.agenticrag.domain.model.KnowledgeSource;
import com.example.agenticrag.infra.persistence.KnowledgeApprovalRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Coração da Fase 1 (Structured RAG).
 *
 * <p>Fluxo: recupera do pgvector (memória de longo prazo) os chunks do componente ->
 * monta contexto com citações -> pede à LLM uma explicação de "como consumir" ancorada
 * apenas nesse contexto -> aplica memória de curto prazo (conversa) -> salva a resposta
 * como rascunho para aprovação humana (que, se aprovada, realimenta a base).
 *
 * <p>Optamos por recuperação MANUAL (em vez do QuestionAnswerAdvisor) para devolver
 * citações estruturadas e ter controle explícito sobre filtro por componente e rerank.
 */
@Service
public class ComponentAdvisorService {

    private static final int TOP_K = 6;

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

    public ComponentAdvisorService(ChatClient chatClient,
                                   VectorStore vectorStore,
                                   KnowledgeApprovalRepository approvals) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.approvals = approvals;
    }

    public AdviceResult advise(String componentId, String question, String conversationId) {
        String cid = StringUtils.hasText(conversationId) ? conversationId : UUID.randomUUID().toString();
        String safeComponentId = requireSafe(componentId);

        // 1) Recuperação semântica filtrada pelo componente (memória de longo prazo)
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(TOP_K)
                .filterExpression("component_id == '" + safeComponentId + "'")
                .build());

        boolean grounded = !docs.isEmpty();
        List<Citation> citations = toCitations(docs);
        String context = buildContext(citations);

        // 2) Geração ancorada no contexto + memória de curto prazo (advisor padrão do ChatClient)
        String userMessage = """
                Componente: %s
                Pergunta: %s

                CONTEXTO (fontes numeradas):
                %s
                """.formatted(componentId, question,
                grounded ? context : "(nenhum contexto recuperado — a base para este componente pode não ter sido ingerida)");

        String answer = chatClient.prompt()
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, cid))
                .system(SYSTEM_PROMPT)
                .user(userMessage)
                .call()
                .content();

        // 3) Persiste rascunho para o loop de aprovação humana (Corrective/Feedback RAG)
        UUID approvalId = approvals.savePending(componentId, question, answer);

        return new AdviceResult(componentId, cid, answer, citations, grounded, approvalId);
    }

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

    private static String buildContext(List<Citation> citations) {
        StringBuilder sb = new StringBuilder();
        for (Citation c : citations) {
            sb.append('[').append(c.index()).append("] (").append(c.source()).append(" - ")
                    .append(c.ref()).append(")\n").append(c.snippet()).append("\n\n");
        }
        return sb.toString();
    }

    private static String snippet(String text) {
        if (text == null) {
            return "";
        }
        String t = text.strip();
        return t.length() <= 600 ? t : t.substring(0, 600) + " …";
    }

    private static String requireSafe(String id) {
        if (id == null || !id.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("component_id inválido: " + id);
        }
        return id;
    }
}
