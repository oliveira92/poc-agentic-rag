package com.example.agenticrag.security;

/**
 * Onde o controle roda. As três superfícies exigidas no M04 — e a razão de a ação ser
 * configurada <b>por estágio</b>: o mesmo achado tem consequência diferente dependendo de
 * onde aparece (um CPF na pergunta do titular se mascara; um CPF entrando na base de
 * conhecimento se bloqueia, porque ali ele ficaria indexado para sempre).
 */
public enum GuardStage {

    /** Conteúdo entrando na base vetorial (ingestão) — o que fica gravado. */
    INGESTION,

    /** Pergunta do usuário, ANTES do prompt e da recuperação. */
    INPUT,

    /** Resposta do modelo, ANTES de chegar ao usuário e de virar rascunho HITL. */
    OUTPUT
}
