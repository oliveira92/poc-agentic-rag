# ADR-0005 — Roteamento por risco in-process (alias de negócio → modelo)

- **Status:** Aceito
- **Data:** 2026-07-17
- **Camada M03:** 5 (Governança) · 3 (Qualidade)

## Contexto

A A03/A05 ensina "responder ancorado + **rotear por risco**": rota de risco (dinheiro, auth)
merece o modelo forte; FAQ trivial pode usar o rápido/barato (gráfico "−44%" de custo). No
treinamento isso é o gateway **LiteLLM** (`config.yaml` central). O PoC não sobe um gateway,
mas o **conceito** deve existir.

## Decisão

Implementar a ideia **in-process** com
[`RouteClassifier`](../../src/main/java/com/example/agenticrag/routing/RouteClassifier.java):
classifica `(componentId, pergunta)` em rota + `RiskTier` e resolve o **modelo real** via
[`RoutingProperties`](../../src/main/java/com/example/agenticrag/routing/RoutingProperties.java)
("aliases de negócio → modelo"). O modelo escolhido é aplicado por chamada
(`ChatOptions.builder().model(...)`) e vira dimensão de métrica/trace.

Por padrão os três aliases (`strong/fast/default`) apontam para o **mesmo** modelo →
**comportamento inalterado**. Apontar `app.routing.fast-model` para um modelo mais barato ativa
a economia, mantendo o forte nas rotas de risco.

## Consequências

- ✅ Escolha de modelo por rota vira decisão central e **observável** (não espalhada em controllers).
- ✅ Alavanca de ROI (A04) pronta: tokens dominam o custo; rotear é a economia mais direta.
- ✅ Migração futura para um gateway real (LiteLLM) não muda o app — só move a política.
- ⚠️ Heurística por palavra-chave é simples e transparente; um roteador-LLM fica para a Fase 3.

## Alternativas consideradas

- **Subir o LiteLLM na PoC:** operação extra sem necessidade nesta fase; adiado.
- **Sempre o modelo forte:** simples, mas caro — desperdiça o ROI que a rota destrava.
