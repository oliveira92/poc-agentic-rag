# ADR-0001 — Observabilidade vendor-neutral (OpenTelemetry/Micrometer → Langfuse)

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 1 (O que Observar) e 2 (Métricas Técnicas)

## Contexto

Em sistemas de IA o sucesso está no **conteúdo**, não no HTTP 200 (A01). Precisávamos
enxergar o pipeline (retrieval → chat), capturar tokens/latência/status e correlacionar tudo
por `trace_id`, sem prender o projeto a um SDK proprietário. A stack do treinamento usa a SDK
do Langfuse; a nossa é Java/Spring AI.

## Decisão

Instrumentar via **Micrometer Tracing + OpenTelemetry** (já emitido pelo Spring AI) e exportar
por **OTLP para o Langfuse v3**. Enriquecer o span do `/advise` com atributos no espírito das
*gen_ai semantic conventions* (`gen_ai.request.model`, `gen_ai.usage.*_tokens`, `rag.route`,
`rag.risk`) e **eventos** de negócio (`rag.ungrounded`, `rag.low_confidence`, `user_feedback.*`).
Devolver o `traceId` no envelope da resposta para correlação ponta a ponta.

SLIs técnicos ficam em **Micrometer** (`RagMetrics`) expostos em `/actuator/prometheus`.

## Consequências

- ✅ *Instrumenta uma vez, exporta para qualquer backend* (Langfuse/Datadog/Grafana) — sem lock-in.
- ✅ Métrica e trace compartilham a mesma fonte (os spans), como manda a A01→A02.
- ⚠️ Conteúdo (prompt/resposta) é **PII por padrão**: fica *opt-in* nos spans; produção usa
  storage controlado (padrão 3 da A01). O `flush` do exporter precisa ocorrer (scripts curtos).

## Alternativas consideradas

- **SDK Langfuse direto (como no curso):** acoplaria o app a um fornecedor; descartado.
- **Só logs:** não dá waterfall nem correlação — insuficiente para IA.
