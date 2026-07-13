package com.example.agenticrag.web;

import com.example.agenticrag.approval.KnowledgeApprovalService;
import com.example.agenticrag.domain.model.ApprovalRecord;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Fila de aprovação humana das respostas geradas pela LLM (HITL). */
@RestController
@RequestMapping("/api/v1/approvals")
public class ApprovalController {

    private final KnowledgeApprovalService approval;

    public ApprovalController(KnowledgeApprovalService approval) {
        this.approval = approval;
    }

    public record ReviewRequest(String reviewer, String note) {
    }

    @GetMapping("/pending")
    public List<ApprovalRecord> pending() {
        return approval.listPending();
    }

    /** Aprova: indexa a resposta na base (source=LLM_APPROVED). */
    @PostMapping("/{id}/approve")
    public ApprovalRecord approve(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        ReviewRequest r = req == null ? new ReviewRequest(null, null) : req;
        return approval.approve(id, r.reviewer(), r.note());
    }

    /** Rejeita: descarta sem indexar. */
    @PostMapping("/{id}/reject")
    public ApprovalRecord reject(@PathVariable UUID id, @RequestBody(required = false) ReviewRequest req) {
        ReviewRequest r = req == null ? new ReviewRequest(null, null) : req;
        return approval.reject(id, r.reviewer(), r.note());
    }
}
