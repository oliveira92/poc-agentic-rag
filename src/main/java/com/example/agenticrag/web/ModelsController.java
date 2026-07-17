package com.example.agenticrag.web;

import com.example.agenticrag.model.ModelCatalogService;
import com.example.agenticrag.model.ModelInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lista os modelos Anthropic selecionáveis: o catálogo (allow-list versionada) somado à
 * descoberta ao vivo dos modelos da conta ({@code GET /v1/models}). O cliente usa um destes
 * ids no campo {@code model} do {@code POST /advise} para escolher o modelo por chamada.
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelsController {

    private final ModelCatalogService catalog;

    public ModelsController(ModelCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    public List<ModelInfo> list() {
        return catalog.available();
    }
}
