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

    /** Ingere usando um README markdown enviado no corpo (text/markdown). */
    @PostMapping(value = "/ingest/readme", consumes = {MediaType.TEXT_MARKDOWN_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public IngestionResult ingestReadme(@PathVariable String componentId, @RequestBody String markdown) {
        return ingestion.ingestFromPortalWithReadme(componentId, markdown);
    }
}
