package com.example.agenticrag.web;

import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.RateLimitException;
import com.example.agenticrag.model.UnknownModelException;
import com.example.agenticrag.security.GuardrailViolationException;
import com.openai.errors.OpenAIException;
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

    /** Violação de guardrail (M04): entrada/saída/ingestão barrada por controle — 422. */
    @ExceptionHandler(GuardrailViolationException.class)
    public ProblemDetail onGuardrail(GuardrailViolationException ex) {
        log.info("Guardrail bloqueou {}: {}", ex.stage(), ex.controlIds());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setTitle("Bloqueado por política de segurança");
        pd.setProperty("stage", ex.stage().name());
        pd.setProperty("controls", ex.controlIds());
        // Só o id do controle e a categoria saem daqui — nunca o trecho que casou, que é
        // justamente o dado sensível (ou o payload de injeção) que não pode vazar na resposta.
        pd.setProperty("findings", ex.publicFindings());
        return pd;
    }

    // Spring AI 2.0: cada módulo usa o SDK oficial do vendor (com.anthropic.*, com.openai.*),
    // que propaga sua própria hierarquia de exceções (não mais org.springframework.ai.retry.*).
    // Com o gateway LiteLLM as falhas chegam pelo caminho OpenAI-compatível, mesmo quando o
    // modelo por trás é de outro vendor — por isso os dois pares de handlers.
    // Nota: acoplamento ao provedor isolado aqui; poderia virar exceção de domínio no futuro.

    /** Rate limit do provedor/gateway (429) — transitório, cliente pode tentar de novo. */
    // FQN no segundo: os dois SDKs batizaram a classe de RateLimitException.
    @ExceptionHandler({RateLimitException.class, com.openai.errors.RateLimitException.class})
    public ProblemDetail onAiRateLimit(RuntimeException ex) {
        log.warn("Rate limit do provedor de LLM: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("Provedor de LLM indisponível (rate limit), tente novamente");
        return pd;
    }

    /** Demais erros do provedor de LLM (400 de billing/modelo, 401, 5xx). */
    @ExceptionHandler({AnthropicException.class, OpenAIException.class})
    public ProblemDetail onAiError(RuntimeException ex) {
        log.warn("Falha do provedor de LLM: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
        pd.setTitle("Falha no provedor de LLM");
        return pd;
    }
}
