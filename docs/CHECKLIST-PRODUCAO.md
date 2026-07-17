# Checklist de Produção + Evidências (LGPD) — Agentic RAG

> Camada 5 (A05). *"Cada item existe porque alguém já se queimou sem ele — a checklist é
> cicatriz virada em processo."* Estado atual da PoC marcado; o que falta vira backlog.

## Checklist de produção para IA

| Item | Por que existe | Estado na PoC |
|---|---|---|
| **Prompt versionado** | saber o que mudou quando a qualidade cair | ✅ no código (git) |
| **Golden set** | ter com que testar antes de subir | ✅ [`golden/payments-sdk.csv`](../src/main/resources/golden/payments-sdk.csv) |
| **Modelo versionado junto (prompt+golden+modelo)** | `qualidade = f(prompt, golden, modelo)` | ✅ `app.routing.*` em config |
| **Quality gate no CI** | barrar regressão antes do cliente | ✅ [`quality-gate.yml`](../.github/workflows/quality-gate.yml) |
| **Roteamento por risco** | modelo forte no que é caro de errar | ✅ `RouteClassifier` |
| **Observabilidade (trace/custo/scores)** | diagnosticar sem reproduzir | ✅ OTLP→Langfuse + `/actuator/prometheus` |
| **SLO por modelo/rota** | alertar antes do SLA | ✅ definido (ADR-0002) · ⚠️ alertas a ligar |
| **Guardrail anti-alucinação** | não afirmar endpoint fora das fontes | ✅ `CitationGroundingChecker` |
| **Rollback** | voltar à versão boa em minutos | ⚠️ jar versionado; runbook a escrever |
| **Hardening (rate limit, timeout, PII)** | abuso e vazamento | ⚠️ AuthN/Z é **HU-09** (bloqueante pré-prod) |
| **Logs + consentimento (LGPD)** | rastreabilidade e base legal | ⚠️ conteúdo é PII: opt-in; ver evidências abaixo |
| **Rotina daily/weekly/monthly** | plano não virar papel morto | ✅ definida no [Plano](PLANO-OBSERVABILIDADE-QUALIDADE.md) |

## Evidências para compliance (LGPD) — uma página por caso

> "Chato de fazer, salva-vidas na auditoria" (A05). Modelo preenchido para o caso da PoC.

| Campo | Pergunta que responde | Component Advisor (PoC) |
|---|---|---|
| **Origem** | de onde veio o dado? | Portal de componentes (API + README) + Q&A aprovado por humano |
| **Autorização / base legal** | quem consentiu / qual base? | Dado técnico interno (docs de componente); **sem PII de cliente** no corpus |
| **Processamento** | quem acessa e como anonimiza? | Embeddings locais (ONNX, sem envio externo); chat via provedor LLM configurado |
| **Conteúdo sensível no trace** | prompt/resposta são PII? | **Opt-in**: default só metadados; conteúdo em storage controlado (padrão 3 da A01) |
| **Retenção** | quanto guarda, depois o quê? | ⚠️ definir política de retenção de traces/memória de conversa |
| **Responsável** | quem responde por isso? | ⚠️ atribuir dono (Platform Engineer) |

## Riscos abertos (pré-produção)

1. **Sem AuthN/Z** nos endpoints — **HU-09** (bloqueante). Proteger `/approvals/*` e `/quality/*`.
2. **PII em prompt/resposta** — manter conteúdo *opt-in* nos spans; definir mascaramento
   quando o corpus incluir dados de cliente.
3. **SLO sem alerta** — os alvos existem (ADR-0002); falta ligar o alerta (burn rate) no
   backend de métricas.
4. **Retenção/rollback** — escrever runbook de rollback e política de retenção.
