package com.example.agenticrag.web;

import com.example.agenticrag.advisor.AdviceResult;
import com.example.agenticrag.advisor.Citation;
import com.example.agenticrag.advisor.ComponentAdvisorService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Pergunta ao agente como consumir/implementar um componente. */
@RestController
@RequestMapping("/api/v1/components/{componentId}")
public class AdvisorController {

    private final ComponentAdvisorService advisor;

    public AdvisorController(ComponentAdvisorService advisor) {
        this.advisor = advisor;
    }

    public record AdviseRequest(@NotBlank String question, String conversationId) {
    }

    @PostMapping("/advise")
    public AdviceResult advise(@PathVariable String componentId,
                               @org.springframework.web.bind.annotation.RequestBody AdviseRequest request) {
        return advisor.advise(componentId, request.question(), request.conversationId());
    }

    /** Recuperação pura (sem LLM): inspeciona o ranking das citações para uma consulta. */
    @GetMapping("/search")
    public List<Citation> search(@PathVariable String componentId,
                                 @RequestParam String q,
                                 @RequestParam(defaultValue = "5") int k) {
        return advisor.retrieve(componentId, q, k);
    }
}
