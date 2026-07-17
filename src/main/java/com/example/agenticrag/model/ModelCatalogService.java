package com.example.agenticrag.model;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fonte única para a seleção de modelos (governança — A05). Concilia:
 * <ul>
 *   <li>o <b>catálogo</b> (allow-list versionada em config) — determinístico, offline;</li>
 *   <li>a <b>descoberta ao vivo</b> ({@code /v1/models} da conta) — ids exatos e atuais.</li>
 * </ul>
 *
 * <p>Discovery ({@link #available()}) mostra os dois; a validação ({@link #resolve}) da seleção
 * manual usa o catálogo + o cache ao vivo (sem forçar rede a cada chamada). O flag
 * {@code app.models.allow-any} é a válvula de escape para contas com ids fora do catálogo.
 */
@Service
public class ModelCatalogService {

    private final ModelsProperties props;
    private final AnthropicModelsClient live;

    public ModelCatalogService(ModelsProperties props, AnthropicModelsClient live) {
        this.props = props;
        this.live = live;
    }

    /** Modelos apresentados ao cliente: catálogo + descoberta ao vivo (dedup por id). */
    public List<ModelInfo> available() {
        Map<String, ModelInfo> byId = new LinkedHashMap<>();
        for (ModelInfo m : catalog()) {
            byId.put(m.id(), m);
        }
        if (props.liveSync()) {
            for (ModelInfo m : live.list()) {          // força o fetch (com cache/TTL)
                byId.putIfAbsent(m.id(), m);            // catálogo tem precedência de metadados
            }
        }
        return List.copyOf(byId.values());
    }

    /** A allow-list curada e versionada (config). */
    public List<ModelInfo> catalog() {
        List<ModelInfo> out = new ArrayList<>();
        for (ModelsProperties.Entry e : props.catalog()) {
            out.add(new ModelInfo(e.id(), e.label(), e.tier(), e.description(), "catalog"));
        }
        return out;
    }

    /**
     * Valida a seleção manual de modelo.
     *
     * @return o id validado, ou {@code null} quando nada foi pedido (aí o roteamento por risco decide)
     * @throws UnknownModelException se o id não está na allow-list nem no cache ao vivo (e allow-any=false)
     */
    public String resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return null;
        }
        String id = requested.trim();
        if (props.allowAny() || isAllowed(id)) {
            return id;
        }
        throw new UnknownModelException(id);
    }

    /** Validação rápida: catálogo + cache ao vivo (não força rede). */
    public boolean isAllowed(String id) {
        for (ModelsProperties.Entry e : props.catalog()) {
            if (e.id().equals(id)) {
                return true;
            }
        }
        for (ModelInfo m : live.cached()) {
            if (m.id().equals(id)) {
                return true;
            }
        }
        return false;
    }
}
