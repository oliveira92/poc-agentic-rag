# ADR-0002 — SLIs e SLO por modelo e por rota de risco

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 2 (Métricas Técnicas)

## Contexto

Um p95 global mistura o que não deveria ser somado: um FAQ de 500 ms e uma pergunta de risco
de 5 s. A A02 é explícita: *"1 SLO para vários modelos é mentira"* e *"a média mente"* —
percentis (p95) são robustos à cauda; a média é arrastada pelo azarado.

## Decisão

Toda métrica carrega as dimensões **`model`** e **`route`**. Latências publicam **p50/p95/p99**
(`Timer.publishPercentiles`). SLOs são definidos **por rota de risco**, estimados de
*baseline + margem*, com a regra **SLO < SLA**:

| Rota | SLO p95 | SLO groundedness |
|---|---|---|
| faq (baixo) | ≤ 3 s | ≥ 0,70 |
| integracao (médio) | ≤ 5 s | ≥ 0,80 |
| risco (alto) | ≤ 6 s | ≥ 0,90 |

Erros são quebrados por **tipo** (`rate_limit`/`timeout`/`other`) porque cada tipo tem uma ação
diferente (backoff/rotear · fallback rápido · investigar).

## Consequências

- ✅ Alertas e dashboards ganham responsável (o `model`/`route` que quebrou o SLO).
- ✅ Base pronta para *error budget* e *burn rate* (A02) quando houver histórico real.
- ⚠️ Os alvos são **propostas**; precisam ser recalibrados com o baseline de produção.

## Alternativas consideradas

- **SLO único global:** simples, mas esconde o culpado e gera alerta inacionável. Descartado.
