package com.example.agenticrag.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Uma resposta da LLM aguardando (ou já submetida a) revisão humana.
 * Após {@link ApprovalStatus#APPROVED} vira um chunk na base vetorial.
 */
public record ApprovalRecord(
        UUID id,
        String componentId,
        String question,
        String answer,
        ApprovalStatus status,
        String reviewer,
        String reviewNote,
        Instant createdAt,
        Instant reviewedAt,
        String vectorDocId) {
}
