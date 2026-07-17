# ADR-0003 — Qualidade: guardrail determinístico + LLM-as-judge

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 3 (Qualidade da Resposta)

## Contexto

*Rápido (A02) sem certo (A03) é rápido para errar.* O inimigo é a **alucinação confiante** — a
resposta mais fluente é a mais perigosa. Precisávamos medir *groundedness* (cada afirmação está
apoiada no contexto?) em escala, sem depender só de um juiz que tem **viés**.

## Decisão

Duas linhas de defesa complementares:

1. **Guardrail determinístico, sempre ligado, custo zero** (`CitationGroundingChecker`):
   extrai endpoints citados no texto e verifica contra as citações recuperadas. Divergência →
   `lowConfidence` (evento no trace + meter). Pega o "mentiroso confiante" sem gastar chamada.
2. **LLM-as-judge** (`GroundednessEvaluator`) com nota 1–5 + justificativa:
   - `mock` (default): heurística determinística — **roda no CI sem chave**;
   - `llm`: juiz real via `ChatClient` isolado (avaliação cega).

O resultado sempre carrega `judge` e `reason` — o plano exige **calibração contra humano** numa
amostra antes de confiar cego no automático.

## Consequências

- ✅ Anti-alucinação já em produção sem custo (guardrail) — endereça a HU-02 do backlog.
- ✅ Avaliação escalável e reprodutível no CI (mock) e fiel em produção (llm/amostragem).
- ⚠️ O juiz-LLM premia verbosidade/fluência — nunca aprovar por fluência; calibrar sempre.

## Alternativas consideradas

- **Só accuracy vs. gabarito:** texto gerado tem mil formas certas; comparar string quebra.
- **Só juiz-LLM, sem guardrail:** custo/latência por chamada e risco de automatizar o viés.
