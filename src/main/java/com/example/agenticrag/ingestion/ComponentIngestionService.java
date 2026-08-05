package com.example.agenticrag.ingestion;

import com.example.agenticrag.domain.model.ComponentDetails;
import com.example.agenticrag.embedding.EmbeddingProperties;
import com.example.agenticrag.domain.model.ComponentEndpoint;
import com.example.agenticrag.domain.model.KnowledgeSource;
import com.example.agenticrag.domain.port.ComponentPortalPort;
import com.example.agenticrag.infra.persistence.ComponentRegistryRepository;
import com.example.agenticrag.security.GuardDecision;
import com.example.agenticrag.security.GuardrailViolationException;
import com.example.agenticrag.security.IngestionGuard;
import com.example.agenticrag.security.scope.ScopeContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Fluxo de ingestão da Fase 1.
 *
 * <p>Fontes primárias -> documentos -> embeddings -> pgvector (memória de longo prazo):
 * <ul>
 *   <li>metadados estruturados do portal (overview + 1 doc por endpoint);</li>
 *   <li>README markdown (chunked por seção).</li>
 * </ul>
 *
 * <p>Idempotência por hash de conteúdo. Ao reingerir, apaga apenas as fontes primárias
 * do componente e <b>preserva</b> o conhecimento aprovado pela LLM (source=llm_approved).
 */
@Service
public class ComponentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ComponentIngestionService.class);

    private final ComponentPortalPort portal;
    private final VectorStore vectorStore;
    private final ComponentRegistryRepository registry;
    private final MarkdownChunker chunker;
    private final IngestionGuard guard;
    private final String embeddingModelId;

    public ComponentIngestionService(ComponentPortalPort portal,
                                     VectorStore vectorStore,
                                     ComponentRegistryRepository registry,
                                     MarkdownChunker chunker,
                                     IngestionGuard guard,
                                     EmbeddingProperties embedding) {
        this.portal = portal;
        this.vectorStore = vectorStore;
        this.registry = registry;
        this.chunker = chunker;
        this.guard = guard;
        this.embeddingModelId = embedding.modelId();
    }

    public IngestionResult ingestFromPortal(String componentId) {
        ComponentDetails details = portal.fetchComponent(componentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Componente '" + componentId + "' não encontrado no portal."));
        String readme = portal.fetchReadme(componentId).orElse(null);
        return ingest(details, readme);
    }

    /**
     * Ingestão via arquivo markdown enviado pelo usuário (README da aplicação):
     * usa os metadados estruturados do portal + o README fornecido no lugar do do portal.
     */
    public IngestionResult ingestFromPortalWithReadme(String componentId, String readmeMarkdown) {
        ComponentDetails details = portal.fetchComponent(componentId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Componente '" + componentId + "' não encontrado no portal."));
        return ingest(details, readmeMarkdown);
    }

    public IngestionResult ingest(ComponentDetails details, String readme) {
        String componentId = details.id();
        List<Document> docs = new ArrayList<>();

        // 0) M04 — o README é a fonte NÃO CONFIÁVEL desta ingestão (vem de fora do portal, pode
        //    ter sido editado por qualquer um). Vai ao guard ANTES do chunking: assunto e
        //    segredo se avaliam no documento inteiro, não em pedaços de 800 caracteres, e um
        //    documento reprovado não deve nem chegar a virar chunk.
        //    A moldura de escopo vem do PORTAL (metadado estruturado, fonte de verdade) — é o
        //    que permite reprovar a "receita de bolo" enviada como README de um SDK de pagamentos.
        ScopeContext scope = new ScopeContext(componentId,
                details.name() + ": " + nz(details.description()) + " " + String.join(" ", details.tags()),
                details.endpoints().stream().map(e -> nz(e.method()) + " " + nz(e.path())).toList());
        String safeReadme = requireSafe(readme, scope, "README de " + componentId);

        // 1) Overview estruturado
        docs.add(doc(overviewText(details), Map.of(
                KnowledgeSource.METADATA_KEY, KnowledgeSource.PORTAL_API.name(),
                "component_id", componentId,
                "component_name", nz(details.name()),
                "version", nz(details.version()),
                "kind", "overview")));

        // 2) Um documento por endpoint (dados já estruturados => metadado filtrável)
        for (ComponentEndpoint ep : details.endpoints()) {
            docs.add(doc(endpointText(details, ep), Map.of(
                    KnowledgeSource.METADATA_KEY, KnowledgeSource.PORTAL_API.name(),
                    "component_id", componentId,
                    "version", nz(details.version()),
                    "kind", "endpoint",
                    "endpoint", nz(ep.method()) + " " + nz(ep.path()))));
        }
        int endpointsIndexed = details.endpoints().size();

        // 3) README chunked por seção (já sanitizado no passo 0)
        int readmeChunks = 0;
        if (safeReadme != null && !safeReadme.isBlank()) {
            for (MarkdownChunker.Chunk c : chunker.chunk(safeReadme)) {
                docs.add(doc(c.text(), Map.of(
                        KnowledgeSource.METADATA_KEY, KnowledgeSource.README.name(),
                        "component_id", componentId,
                        "version", nz(details.version()),
                        "kind", "readme",
                        "section", c.section())));
                readmeChunks++;
            }
        }

        // 4) M04 — bateria por documento (sem escopo: o portal É a definição de escopo, medir o
        //    portal contra si mesmo não diria nada). Pega o que o passo 0 não pega: um segredo
        //    colado no schema de exemplo de um endpoint viraria embedding, e o RAG passaria a
        //    distribuí-lo com citação para quem perguntasse.
        docs = sanitize(docs, componentId);

        // 5) Idempotência: hash do conteúdo + id do modelo de embeddings. Se nada mudou,
        //    não reprocessa; se o modelo mudou, o hash muda e força o re-embed.
        //    O hash é do conteúdo JÁ SANITIZADO — senão, mudar a política de segurança não
        //    forçaria a re-ingestão e o índice ficaria com o texto da política antiga.
        String hash = contentHash(docs, embeddingModelId);
        if (registry.findSourceHash(componentId).filter(hash::equals).isPresent()) {
            log.info("Ingestão de {} pulada (conteúdo inalterado, hash={}).", componentId, hash);
            return new IngestionResult(componentId, endpointsIndexed, readmeChunks, 0, true);
        }

        // 6) Refresh: apaga fontes primárias antigas, preserva conhecimento aprovado.
        vectorStore.delete("component_id == '" + safe(componentId)
                + "' && " + KnowledgeSource.METADATA_KEY + " != '" + KnowledgeSource.LLM_APPROVED.name() + "'");

        // 7) Embeddings + gravação no pgvector
        vectorStore.add(docs);
        registry.upsert(componentId, details.name(), details.version(), details.description(), hash);

        log.info("Ingerido {}: {} endpoints, {} chunks de README, {} docs.",
                componentId, endpointsIndexed, readmeChunks, docs.size());
        return new IngestionResult(componentId, endpointsIndexed, readmeChunks, docs.size(), false);
    }

    // ---------- guardrails de ingestão (M04) ----------

    /**
     * Roda o guard e devolve o texto que pode ser indexado.
     *
     * @throws GuardrailViolationException quando a política manda recusar — a ingestão inteira
     *                                     falha, e é intencional: aceitar "o resto" de um
     *                                     documento reprovado deixaria a base num estado que
     *                                     ninguém revisou e cujo hash não corresponde à fonte.
     */
    private String requireSafe(String content, ScopeContext scope, String what) {
        if (content == null || content.isBlank()) {
            return content;
        }
        GuardDecision decision = guard.inspect(content, scope);
        if (decision.blocked()) {
            log.warn("Ingestão recusada ({}): controles {}", what, decision.controlIds());
            throw new GuardrailViolationException(decision);
        }
        if (decision.masked()) {
            log.info("Ingestão sanitizada ({}): controles {}", what, decision.controlIds());
        }
        return decision.text();
    }

    /** Mesma bateria por documento, sem o juiz de escopo. */
    private List<Document> sanitize(List<Document> docs, String componentId) {
        List<Document> out = new ArrayList<>(docs.size());
        for (Document d : docs) {
            String safe = requireSafe(d.getText(), null, "documento de " + componentId);
            out.add(safe != null && safe.equals(d.getText())
                    ? d
                    : Document.builder().text(safe).metadata(d.getMetadata()).build());
        }
        return out;
    }

    // ---------- helpers ----------

    private static Document doc(String text, Map<String, Object> metadata) {
        return Document.builder().text(text).metadata(metadata).build();
    }

    private static String overviewText(ComponentDetails d) {
        return """
                Componente: %s (id=%s, versão=%s)
                Descrição: %s
                Tags: %s
                Repositório: %s
                """.formatted(nz(d.name()), d.id(), nz(d.version()), nz(d.description()),
                String.join(", ", d.tags()), nz(d.repositoryUrl()));
    }

    private static String endpointText(ComponentDetails d, ComponentEndpoint ep) {
        return """
                Endpoint do componente %s (v%s): %s %s
                Descrição: %s
                Autenticação necessária: %s
                Request: %s
                Response: %s
                """.formatted(nz(d.name()), nz(d.version()), nz(ep.method()), nz(ep.path()),
                nz(ep.summary()), ep.authRequired() ? "sim" : "não",
                nz(ep.requestSchema()), nz(ep.responseSchema()));
    }

    private static String contentHash(List<Document> docs, String embeddingModelId) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(embeddingModelId.getBytes(StandardCharsets.UTF_8));
            for (Document d : docs) {
                md.update(d.getText().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Sanitização básica para uso do id em filter expression do vector store. */
    private static String safe(String id) {
        if (!id.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("component_id inválido: " + id);
        }
        return id;
    }
}
