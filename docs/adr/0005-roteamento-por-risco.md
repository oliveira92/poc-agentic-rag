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

O roteamento por custo está **ativo por padrão**: `fast-model=claude-haiku-4-5` (FAQ/baixo risco)
e `strong-model=claude-sonnet-5` (rotas de risco). Medido na PoC: FAQ→Haiku ~15× mais barato e
~7× mais rápido que risco→Sonnet. Para desligar a economia, basta igualar os aliases; os overrides
`APP_ROUTING_*` permitem ajustar por ambiente.

## Consequências

- ✅ Escolha de modelo por rota vira decisão central e **observável** (não espalhada em controllers).
- ✅ Alavanca de ROI (A04) pronta: tokens dominam o custo; rotear é a economia mais direta.
- ✅ Migração futura para um gateway real (LiteLLM) não muda o app — só move a política.
- ⚠️ Heurística por palavra-chave é simples e transparente; um roteador-LLM fica para a Fase 3.

## Alternativas consideradas

- **Subir o LiteLLM na PoC:** operação extra sem necessidade nesta fase; adiado.
- **Sempre o modelo forte:** simples, mas caro — desperdiça o ROI que a rota destrava.
