# Plano de Observabilidade e Qualidade — Agentic RAG (Component Advisor)

> **Entregável do módulo M03 — Observabilidade e Qualidade em Produção (Capstone / A06).**
> As **5 camadas** ensinadas (A01–A05) aplicadas ao PoC **sem mudar o conceito** do que já
> existia: a Fase 1 (Structured RAG) continua igual — o que este plano faz é **instrumentá-la**
> para que se possa *enxergar, medir, avaliar, justificar e operar*.

## O caso, em uma frase

Um agente que ensina desenvolvedores a **consumir componentes internos** (API do portal +
README), respondendo *"como implementar o consumo de X"* com **citações**, e que aprende com
**curadoria humana** (HITL). Ver [README](../README.md) para a arquitetura.

## Tradução da stack do treinamento → a stack do PoC

O treinamento usou Python · LiteLLM · Langfuse v3 · Azure AI Foundry Evals. O PoC é Java/Spring.
Os **conceitos são idênticos**; muda a ferramenta que os encarna:

| Conceito do M03 | No treinamento | Neste PoC (Java/Spring AI 2.0) |
|---|---|---|
| Trace/span/evento (A01) | Langfuse SDK `@observe` | Spring AI + Micrometer Tracing → **OTLP → Langfuse** |
| SLIs técnicos (A02) | pandas sobre `traces.csv` | **Micrometer** meters → `/actuator/prometheus` |
| LLM-as-judge (A03) | Foundry `GroundednessEvaluator` | `GroundednessEvaluator` (mock + LLM via `ChatClient`) |
| Golden set + gate (A05) | `golden.csv` + `quality_gate.py` | `golden/payments-sdk.csv` + `QualityGate` + workflow |
| Gateway/roteamento (A05) | LiteLLM Router (`config.yaml`) | `RouteClassifier` in-process (alias→modelo por risco) |
| Score/feedback no trace | Langfuse `score_current_trace` | tags/eventos no span via `Tracer` + meters |

> A observabilidade é **vendor-neutral por design** (OpenTelemetry): instrumenta-se uma vez e
> exporta-se para Langfuse, Datadog ou Grafana sem reescrever o app — a mensagem da A01.

---

## As 5 camadas — o que foi aplicado

### 🟦 Camada 1 — O que Observar (A01): traces, spans e eventos

**Princípio:** em IA, o sucesso está no **conteúdo**, não no HTTP 200. O trace tem que guardar
o que entrou, o que saiu e por onde passou — para reconstruir a falha *sem* reproduzir o bug.

**Implementado** ([`ComponentAdvisorService`](../src/main/java/com/example/agenticrag/advisor/ComponentAdvisorService.java)):

- **Pipeline visível como spans**: `retrieval` (pgvector) → `chat` (LLM). O Spring AI já emite
  os spans de `chat`/`embedding`; enriquecemos o span do `/advise` com **atributos** e **eventos**.
- **Atributos no span** (nomes no espírito das *gen_ai conventions* da A01):
  `rag.component_id`, `rag.route`, `rag.risk`, `gen_ai.request.model`,
  `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`.
- **Eventos** (o 4º sinal — o "fato de negócio"): `rag.ungrounded` (nada recuperado),
  `rag.low_confidence` (guardrail de fidelidade disparou), `rag.error`, e
  `user_feedback.positive/negative` (👍/👎 do cliente).
- **`trace_id` amarra tudo**: o `/advise` **devolve o `traceId`** no envelope
  ([`AdviceResult`](../src/main/java/com/example/agenticrag/advisor/AdviceResult.java)),
  para o cliente correlacionar a resposta ao feedback (A04) e o suporte cair direto na chamada.
- **Onde vive:** Langfuse v3 via OTLP (perfil `observability` do `docker-compose.yml`).

**Postura de PII (A01 "input/output são PII por padrão"):** por padrão o trace carrega
**metadados e estrutura** (modelo, tokens, rota, scores), **não** o conteúdo bruto. O anexo de
prompt/completion aos spans é *opt-in* (`spring.ai.chat.client.observations.log-prompt`), pensado
para pré-produção. Em produção, padrão 3 da aula: conteúdo em storage controlado, trace referencia.

### 🟩 Camada 2 — Métricas Técnicas (A02): SLI · SLO · erro · saturação

**Princípio:** *"1 SLO para vários modelos é mentira"* e *"a média mente"* — toda métrica
carrega a dimensão `model`/`route` e latências publicam **p50/p95/p99**.

**Implementado** ([`RagMetrics`](../src/main/java/com/example/agenticrag/observability/RagMetrics.java)),
expostos em `/actuator/prometheus` e `/actuator/metrics`:

| SLI | Meter (Micrometer) | Dimensões | O que responde |
|---|---|---|---|
| Latência do /advise | `rag.advise.latency` (p50/p95/p99) | `route`, `model`, `outcome` | rápido? por rota/modelo |
| Latência da recuperação | `rag.retrieve.latency` | `route` | o gargalo é o retrieval ou o LLM? |
| Qualidade bruta do retrieval | `rag.retrieve.top_score` | `route` | recuperou algo relevante? |
| Tokens | `rag.llm.tokens` | `model`, `type=input\|output` | **o custo mora aqui** (A04) |
| Custo estimado | `rag.llm.cost` | `model` | R$/US$ por modelo |
| Sem base recuperada | `rag.advise.ungrounded` | `route` | quantas respostas sem fonte |
| Guardrail disparou | `rag.advise.low_confidence` | `route` | risco de alucinação (A03) |
| Erros | `rag.errors` | `model`, `type=rate_limit\|timeout\|other` | cada tipo, uma ação |

**SLO por modelo/rota (proposto — baseline + margem, como na A02):**

| Rota | Risco | SLO p95 (latência) | SLO groundedness | Modelo |
|---|---|---|---|---|
| `faq` | baixo | ≤ 3 s | ≥ 0,70 | rápido |
| `integracao` | médio | ≤ 5 s | ≥ 0,80 | default |
| `risco` (pagamento/auth/erro) | alto | ≤ 6 s | ≥ 0,90 | forte |

Regra de ouro da A02: **SLO < SLA, sempre** — o SLO (amarelo interno) soa antes do SLA (multa).
Os thresholds de groundedness moram em
[`RoutingProperties`](../src/main/java/com/example/agenticrag/routing/RoutingProperties.java).

**Erros com tipo → ação** (A02): `429/rate_limit` → backoff/rotear · `timeout` → fallback rápido ·
`other` → investigar. Classificados em `ComponentAdvisorService.classifyError`.

### 🟧 Camada 3 — Qualidade da Resposta (A03): groundedness + o juiz

**Princípio:** *rápido (A02) sem certo (A03) é rápido para errar.* A alucinação mais perigosa é
a **mais fluente** — nunca aprovar por fluência.

**Implementado** — duas linhas de defesa:

1. **Guardrail determinístico, sempre ligado e de custo zero**
   ([`CitationGroundingChecker`](../src/main/java/com/example/agenticrag/quality/CitationGroundingChecker.java)):
   extrai os endpoints citados no texto (`POST /v2/...`) e verifica se **constam das citações**.
   Se a resposta cita algo fora das fontes → `lowConfidence=true` + evento no trace + meter.
   É o "mentiroso confiante" pego sem gastar uma chamada (endereça **HU-02** do backlog).

2. **LLM-as-judge** ([`GroundednessEvaluator`](../src/main/java/com/example/agenticrag/quality/GroundednessEvaluator.java)):
   um modelo "juiz" lê `(pergunta, contexto, resposta)` e devolve **nota 1–5 + justificativa**
   seguindo a rubrica de groundedness.
   - `MockGroundednessEvaluator` (**default**): heurística determinística, **roda no CI sem chave**
     ("Sem chave? roda em mock").
   - `LlmGroundednessEvaluator` (`app.quality.judge=llm`): juiz real via `ChatClient` isolado
     (sem memória de conversa → avaliação sempre cega).

**Cuidado ensinado — o juiz tem viés** (verbosidade/fluência/auto-preferência): por isso o
resultado sempre carrega `judge` e `reason` (auditável), e o plano prevê **calibração contra
humano** numa amostra antes de confiar cego no automático.

**Golden set** ([`golden/payments-sdk.csv`](../src/main/resources/golden/payments-sdk.csv)):
13 casos **fácil / médio / armadilha** — as armadilhas (fora de escopo) são as que revelam
alucinação. Versionado no repositório (fonte da verdade do gate).

**Score de volta ao trace:** a nota de groundedness e o `lowConfidence` viram tag/evento no
mesmo trace da A01 — qualidade e observabilidade no mesmo lugar ("filtrar traces com
groundedness < 3").

### 🟨 Camada 4 — Métricas de Negócio (A04): KPI · funil · ROI

**Princípio:** só a métrica de negócio justifica o orçamento. Toda métrica técnica/qualidade
precisa virar uma das **4 moedas** (dinheiro, tempo, risco, receita).

**North-star:** *time-to-first-successful-call* — tempo do dev até a 1ª chamada bem-sucedida ao
componente (já era a métrica-norte do [BACKLOG](BACKLOG.md)).

**3 KPIs com o canvas de 7 casas (A04):**

| KPI | Definição | Fonte | Baseline → Target (90d) | Dono | Freq. |
|---|---|---|---|---|---|
| **Time-to-first-successful-call** | mediana do tempo entre 1ª pergunta e 1ª chamada OK ao componente | Langfuse (sessão) + telemetria do dev | *(medir)* → −40% | Platform Eng | semanal |
| **Taxa de resposta fundamentada** | % de `/advise` com `grounded=true` **e** `lowConfidence=false` | `rag.advise.ungrounded` + `rag.advise.low_confidence` | *(medir)* → ≥ 95% | Tech Lead | semanal |
| **CSAT do agente** | média do feedback 👍/👎 por rota | `rag.csat` (endpoint `/feedback`) | *(medir)* → ≥ 0,85 | Product | semanal |

Apoio (leading): nº de componentes ingeridos, % de respostas aprovadas na curadoria (`rag.hitl`).

**CSAT ligado ao trace** (A04) — implementado em
[`FeedbackController`](../src/main/java/com/example/agenticrag/web/FeedbackController.java):
`POST /components/{id}/feedback {value:1|0, route, traceId}` → meter `rag.csat` + evento no span.
Mesma mecânica do score de qualidade, mas quem pontua é o cliente.

**Funil de valor** (onde o valor vaza): Adoção (devs que usaram) → Uso (voltaram?) →
Satisfação (CSAT) → Resultado (integrou de fato). Fonte primária: os próprios traces/sessions.

**ROI** — em assistente com contexto grande, **tokens dominam o custo e são variáveis** (A04).
Alavancas já disponíveis no PoC: **roteamento por rota** (modelo certo p/ cada risco), prompts
enxutos e (Fase 2) cache. Custo por chamada é medido em `rag.llm.cost`/`rag.llm.tokens`.

### 🟥 Camada 5 — Governança e Produção (A05): checklist · gate · roteamento · rotina

**Princípio:** deixa de *medir* e passa a *operar*. *"No notebook funciona; em produção, quem segura?"*

**Versionar a tríade** (A05): `qualidade = f(prompt, golden, modelo)`. Prompt (no código),
golden set (`golden/*.csv`) e modelo (config `app.routing.*`) versionam **juntos** — trocar um é
uma versão nova e um novo teste.

**Quality gate no CI — "O Portão"**
([`.github/workflows/quality-gate.yml`](../.github/workflows/quality-gate.yml)):
dispara em PRs que mexem em prompt/golden/modelo/recuperação, roda o golden set pela recuperação
e **barra o merge** se regredir (endpoint `/quality/{id}/gate` → HTTP 422). A regra
([`QualityGate`](../src/main/java/com/example/agenticrag/quality/QualityGate.java)): **toda rota
de risco alto precisa passar** + taxa geral ≥ limite. Foca em **recuperação** — a causa raiz da
maioria das alucinações ("groundedness baixo? olhe o retrieval antes de culpar o modelo").

**Roteamento por risco** — a versão in-process do gateway LiteLLM
([`RouteClassifier`](../src/main/java/com/example/agenticrag/routing/RouteClassifier.java)):
"aliases de negócio → modelo real". Rota de risco (pagamento/auth/idempotência/erro) usa o
modelo **forte**; FAQ conceitual usa o **rápido**. Por padrão os aliases apontam para o mesmo
modelo (**comportamento inalterado**); apontar `app.routing.fast-model` para um modelo mais
barato ativa a economia (gráfico "−44%" da aula), mantendo o forte no risco.

**Seleção de modelo por chamada** — governança de modelo
([ADR-0006](adr/0006-selecao-de-modelos-anthropic.md)): `GET /api/v1/models` lista os modelos
aceitos (allow-list versionada em `app.models` + descoberta ao vivo da conta em `/v1/models`);
o campo `model` do `/advise` escolhe o modelo por chamada (validado, precede o roteamento). O
modelo efetivo entra nas métricas/trace com `rag.model_source = requested | route` — dá para
comparar custo/qualidade entre modelos (A/B) sem tocar no código.

**Checklist de produção + evidências LGPD:** ver [CHECKLIST-PRODUCAO.md](CHECKLIST-PRODUCAO.md).

**Melhoria contínua** — o loop que não termina (A05): *Observar → Diagnosticar → Ajustar →
Medir → Escalar*, com rotina para não virar papel morto:

| Ritmo | O que olhar | Fonte |
|---|---|---|
| **Diário (15 min)** | incidentes, custo e p95 fora do normal | `/actuator/prometheus`, Langfuse |
| **Semanal (1 h)** | tendência de groundedness/CSAT, pior caso, `low_confidence` | meters + scores |
| **Mensal** | revisar SLOs e custo por modelo; decidir roteamento | tabela de SLO acima |

---

## Autoavaliação pela rúbrica do capstone (A06)

| Critério da rúbrica | Como o PoC atende |
|---|---|
| **As 5 camadas aparecem** | A01 spans/eventos · A02 `RagMetrics`/SLO · A03 groundedness+gate · A04 CSAT/KPIs · A05 gate CI/roteamento |
| **Responde ancorado + roteia por risco** | citações obrigatórias + guardrail de fidelidade + `RouteClassifier` (forte no risco) |
| **Trace / custo / scores** | OTLP→Langfuse, `rag.llm.cost`/`tokens`, groundedness/CSAT no trace |
| **Demo de 5 min convence** | roteiro abaixo |

## Roteiro de demo (5 min)

1. **Subir** (perfil `mock`) + ingerir `payments-sdk`. *(A01: cada passo vira trace)*
2. `GET /search?q=estornar` → mostra recuperação e `top_score`. *(A02)*
3. `POST /advise` (pergunta de risco: "como estornar com idempotência?") → resposta com
   citações, `route=risco`, `grounded=true`, `lowConfidence=false`, `traceId`. *(A01/A03/A05)*
4. `POST /advise` com pergunta-armadilha (fora de escopo) → `grounded=false` / `lowConfidence`
   sinaliza o guardrail. *(A03)*
5. `POST /feedback {value:0}` com o `traceId` → CSAT no trace. *(A04)*
6. `POST /quality/payments-sdk/gate` → relatório do golden set + decisão. *(A05)*
7. Abrir `/actuator/prometheus` (p95, tokens, custo por modelo) e o trace no Langfuse. *(A01/A02)*

## Como experimentar cada camada

```bash
BASE=http://localhost:8080/api/v1
curl -s -X POST "$BASE/components/payments-sdk/ingest"
curl -s -X POST "$BASE/components/payments-sdk/advise" -H 'Content-Type: application/json' \
     -d '{"question":"Como estornar uma cobrança tratando idempotência?"}'   # A01/A03/A05
curl -s -X POST "$BASE/components/payments-sdk/feedback" -H 'Content-Type: application/json' \
     -d '{"value":1,"route":"risco","traceId":"<do /advise>"}'               # A04
curl -s -X POST "$BASE/quality/payments-sdk/gate"                            # A05
curl -s localhost:8080/actuator/prometheus | grep rag_                       # A02
```

## Decisões (ADRs)

- [ADR-0001 — Observabilidade vendor-neutral (OTel/Micrometer → Langfuse)](adr/0001-observabilidade-otel-langfuse.md)
- [ADR-0002 — SLIs/SLO por modelo e rota de risco](adr/0002-slo-por-modelo-e-rota.md)
- [ADR-0003 — Qualidade: guardrail determinístico + LLM-as-judge](adr/0003-qualidade-groundedness-e-juiz.md)
- [ADR-0004 — Quality gate de recuperação no CI (golden set)](adr/0004-quality-gate-golden-set.md)
- [ADR-0005 — Roteamento por risco in-process (alias→modelo)](adr/0005-roteamento-por-risco.md)
- [ADR-0006 — Seleção de modelos Anthropic (catálogo versionado + descoberta ao vivo)](adr/0006-selecao-de-modelos-anthropic.md)

*Documento vivo — evolui com as Fases 2–4 do roadmap (HyDE/híbrida, corrective, agentic).*
