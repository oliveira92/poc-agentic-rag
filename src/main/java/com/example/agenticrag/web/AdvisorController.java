package com.example.agenticrag.web;

import com.example.agenticrag.advisor.AdviceResult;
import com.example.agenticrag.advisor.ComponentAdvisorService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
