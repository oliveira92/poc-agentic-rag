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
| **Hardening (rate limit, timeout, PII)** | abuso e vazamento | ✅ guardrails M04 (PII/segredo/injeção/escopo) · ⚠️ AuthN/Z é **HU-09** (bloqueante pré-prod) |
| **Guardrails fora do system prompt** | instrução no prompt não é controle | ✅ 10 controles, política em `app.security` — [SEGURANCA-M04](SEGURANCA-M04.md) |
| **Embedding fora do processo** | modelo no processo = download no boot, 112 MB de RAM e recall pior (quantização) | ✅ padrão pelo gateway LiteLLM, boot 3,3 s sem download (ADR-0009); fallback ONNX local íntegro para offline/CI, com custo medido e fixado em teste |
| **Dataset de segurança no CI** | provar eficácia E custo do controle | ✅ `SecurityDatasetTest` em **dois domínios**: componentes 10/10 ataques e 0/7 FP; A05 8/8 e 0/6 |
| **Sem segredo versionado** | credencial no git é vazamento permanente | ✅ `NoVersionedSecretsTest` · ⚠️ não varre o **histórico** do git |
| **Credencial de vendor fora da app** | reduzir raio de comprometimento | ✅ chaves só no proxy LiteLLM ([ADR-0007](adr/0007-gateway-de-modelos-litellm.md)) |
| **Logs + consentimento (LGPD)** | rastreabilidade e base legal | ⚠️ conteúdo é PII: opt-in; ver evidências abaixo |
| **Rotina daily/weekly/monthly** | plano não virar papel morto | ✅ definida no [Plano](PLANO-OBSERVABILIDADE-QUALIDADE.md) |

## Evidências para compliance (LGPD) — uma página por caso

> "Chato de fazer, salva-vidas na auditoria" (A05). Modelo preenchido para o caso da PoC.

| Campo | Pergunta que responde | Component Advisor (PoC) |
|---|---|---|
| **Origem** | de onde veio o dado? | Portal de componentes (API + README) + Q&A aprovado por humano |
| **Autorização / base legal** | quem consentiu / qual base? | Dado técnico interno (docs de componente); **sem PII de cliente** no corpus |
| **Processamento** | quem acessa e como anonimiza? | Embeddings locais (ONNX, sem envio externo); chat via provedor LLM configurado |
| **Conteúdo sensível no trace** | prompt/resposta são PII? | **Opt-in**: default só metadados; conteúdo em storage controlado (padrão 3 da A01). A partir do M04, PII na pergunta é **ofuscada antes** do trace (SEC-02) |
| **Minimização na entrada** | o dado sensível chega a existir no fluxo? | ✅ CPF/telefone/segredo mascarados antes do prompt, do embedding e do histórico |
| **Dado de terceiro** | o agente entrega dado de quem não perguntou? | ✅ SEC-06 (texto) · ⚠️ SEC-10 (autorização) **inerte** sem identidade propagada — ver risco 5 |
| **Retenção** | quanto guarda, depois o quê? | ⚠️ definir política de retenção de traces/memória de conversa |
| **Responsável** | quem responde por isso? | ⚠️ atribuir dono (Platform Engineer) |

## Riscos abertos (pré-produção)

1. **Sem AuthN/Z** nos endpoints — **HU-09** (bloqueante). Proteger `/approvals/*` e `/quality/*`.
2. **PII em prompt/resposta** — manter conteúdo *opt-in* nos spans; definir mascaramento
   quando o corpus incluir dados de cliente.
3. **SLO sem alerta** — os alvos existem (ADR-0002); falta ligar o alerta (burn rate) no
   backend de métricas.
4. **Retenção/rollback** — escrever runbook de rollback e política de retenção.
5. **SEC-10 inerte** — o controle de autorização a nível de objeto existe e está testado, mas o
   `/advise` não propaga identidade autenticada, então ele não opina. É a maior lacuna do M04 e
   depende de **HU-09**. Ver [SEGURANCA-M04 §6](SEGURANCA-M04.md).
6. **Recall de guardrail não se transfere** — 100% é com n=10 e n=8 ataques, em formulações
   conhecidas. Rodar em dois domínios reduz o risco de sobreajuste, não o tamanho da amostra.
   Ampliar os datasets e ligar o juiz LLM (`app.security.scope-judge=llm`) na rota de risco.
7. **Gateway é ponto único de falha** — proxy LiteLLM fora do ar derruba o `/advise`; falta
   configurar fallback entre modelos.
