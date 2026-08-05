package com.example.agenticrag.web;

import com.example.agenticrag.ingestion.ComponentIngestionService;
import com.example.agenticrag.ingestion.IngestionResult;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ingestão do componente (API do portal e/ou README markdown) para o pgvector. */
@RestController
@RequestMapping("/api/v1/components/{componentId}")
public class IngestionController {

    private final ComponentIngestionService ingestion;

    public IngestionController(ComponentIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    /** Ingere metadados estruturados + README obtidos do portal. */
    @PostMapping("/ingest")
    public IngestionResult ingest(@PathVariable String componentId) {
        return ingestion.ingestFromPortal(componentId);
    }

    /**
     * Ingere usando um README markdown enviado no corpo (text/markdown).
     *
     * <p>O {@code consumes} é a primeira barreira do SEC-01 (415 para {@code application/pdf} e
     * afins), mas é só a barata: content-type é declarado por quem envia. Quem realmente decide
     * é o {@code BinaryContentDetector}, que olha os magic bytes do conteúdo — renomear
     * {@code laudo.pdf} para {@code readme.md} não muda o que está dentro do arquivo.
     */
    @PostMapping(value = "/ingest/readme", consumes = {MediaType.TEXT_MARKDOWN_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public IngestionResult ingestReadme(@PathVariable String componentId, @RequestBody String markdown) {
        return ingestion.ingestFromPortalWithReadme(componentId, markdown);
    }
}
