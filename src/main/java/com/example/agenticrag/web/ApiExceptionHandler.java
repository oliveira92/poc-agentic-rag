package com.example.agenticrag.web;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.example.agenticrag.model.UnknownModelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Erros de domínio como RFC 7807 (ProblemDetail). */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Modelo solicitado fora da allow-list — erro de validação (400). */
    @ExceptionHandler(UnknownModelException.class)
    public ProblemDetail onUnknownModel(UnknownModelException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Modelo não disponível");
        return pd;
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onConflict(IllegalStateException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    // Spring AI 2.0: o módulo Anthropic usa o SDK oficial (com.anthropic.*), que propaga
    // sua própria hierarquia de exceções (não mais org.springframework.ai.retry.*).
    // Nota: acoplamento ao provedor isolado aqui; poderia virar exceção de domínio no futuro.

    /** Rate limit do provedor (429) — transitório, cliente pode tentar de novo. */
    @ExceptionHandler(RateLimitException.class)
    public ProblemDetail onAiRateLimit(RateLimitException ex) {
        log.warn("Rate limit do provedor de LLM: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Provedor de LLM indisponível (rate limit), tente novamente");
        return pd;
    }

    /** Demais erros do provedor de LLM (400 de billing/modelo, 401, 5xx). */
    @ExceptionHandler(AnthropicException.class)
    public ProblemDetail onAiError(AnthropicException ex) {
        log.warn("Falha do provedor de LLM: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Falha no provedor de LLM");
        return pd;
    }
}
