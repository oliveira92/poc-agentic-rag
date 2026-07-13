package com.example.agenticrag.domain.model;

/** Estados do fluxo human-in-the-loop de aprovação de conhecimento gerado pela LLM. */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED
}
